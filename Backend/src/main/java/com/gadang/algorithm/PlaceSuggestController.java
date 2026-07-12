package com.gadang.algorithm;

import com.gadang.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 장소 추천 API
 *
 * GET  /api/places/suggest?lat=35.16&lng=129.16&radius=5000   ← 좌표 기반 (백엔드 Kakao 호출 필요 시)
 * GET  /api/places/suggest?region=부산&radius=3000&topSub=3   ← DataLab 서브지역 트렌드
 * POST /api/places/filter   ← 프론트가 Kakao로 수집한 장소 → Naver 블로그 필터 (주 방식)
 * GET  /api/places/regions  ← 지원 지역 목록
 */

/**
 * 장소 추천 API
 *
 * GET /api/places/suggest?lat=35.16&lng=129.16&radius=5000
 *   → 좌표 기반: Kakao 반경 검색 → Naver 블로그 필터
 *
 * GET /api/places/suggest?region=부산&radius=3000&topSub=3
 *   → 지역명 기반: DataLab 상위 서브지역 → Kakao → Naver 블로그 필터
 *
 * GET /api/places/regions
 *   → 지원하는 지역명 목록
 */
@Slf4j
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceSuggestController {

    private final PlaceFilterService placeFilterService;
    private final ScoredPlaceProvider scoredPlaceProvider;   // 캐시 경유 (Real @Primary)

    /** SSE 스트리밍 전용 — 구역 수집·블로그 채점이 끝날 때까지 요청 스레드를 막지 않도록 별도 풀에서 실행 */
    private static final java.util.concurrent.ExecutorService SSE_POOL =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "places-sse");
                t.setDaemon(true);
                return t;
            });

    /**
     * 좌표 기반 장소 추천
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> suggest(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "3") int topSub,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(defaultValue = "45") int topPercent) {

        List<PlaceCandidate> candidates;

        if (region != null && !region.isBlank()) {
            // 지역명 → 좌표 변환 후 좌표 기반 캐시 경로로 위임 (L1+L2 캐시 공유)
            double[] center = placeFilterService.resolveRegionCenter(region).orElse(null);
            if (center == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("지역 좌표를 찾을 수 없습니다: " + region));
            }
            candidates = scoredPlaceProvider.getScoredPlaces(center[0], center[1], radius, categories, region);
        } else if (lat != null && lng != null) {
            // 좌표 기반 — 3계층 캐시(L1+L2) 경유로 API 절약
            candidates = scoredPlaceProvider.getScoredPlaces(lat, lng, radius, categories);
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("lat/lng 또는 region 파라미터가 필요합니다."));
        }

        // 캐시에는 전체 풀을 저장 — 카테고리별 인기 상위 N%(15/30/45)만 응답 시점에 잘라낸다.
        // 같은 캐시로 여러 topPercent 값을 즉시 서빙할 수 있다.
        List<Map<String, Object>> result = candidates.stream()
                .filter(c -> c.getCategoryPercentile() <= topPercent)
                .map(PlaceSuggestController::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/places/suggest/stream
     * 콜드 캐시(첫 조회)일 때 구역(zone) 단위로 결과를 먼저 보내는 SSE 스트림.
     * 이벤트: "partial"(구역 하나 수집 완료, 미채점 원시 후보) → ... → "complete"(전체 채점·정렬·필터 완료) → "error"(실패 시)
     * 캐시 적중이면 partial 없이 바로 complete 한 번.
     */
    @GetMapping(value = "/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suggestStream(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(defaultValue = "45") int topPercent) {

        SseEmitter emitter = new SseEmitter(300_000L);

        SSE_POOL.execute(() -> {
            try {
                double[] center;
                String hint = null;
                if (region != null && !region.isBlank()) {
                    double[] resolved = placeFilterService.resolveRegionCenter(region).orElse(null);
                    if (resolved == null) {
                        sendEvent(emitter, "error", Map.of("message", "지역 좌표를 찾을 수 없습니다: " + region));
                        emitter.complete();
                        return;
                    }
                    center = resolved;
                    hint = region;
                } else if (lat != null && lng != null) {
                    center = new double[]{lat, lng};
                } else {
                    sendEvent(emitter, "error", Map.of("message", "lat/lng 또는 region 파라미터가 필요합니다."));
                    emitter.complete();
                    return;
                }

                final String finalHint = hint;
                scoredPlaceProvider.streamScoredPlaces(center[0], center[1], radius, categories, finalHint,
                        partial -> sendEvent(emitter, "partial",
                                partial.stream().map(PlaceSuggestController::toMap).collect(Collectors.toList())),
                        complete -> {
                            List<Map<String, Object>> result = complete.stream()
                                    .filter(c -> c.getCategoryPercentile() <= topPercent)
                                    .map(PlaceSuggestController::toMap)
                                    .collect(Collectors.toList());
                            sendEvent(emitter, "complete", result);
                            emitter.complete();
                        });
            } catch (Exception e) {
                log.warn("[Place/SSE] 스트리밍 실패: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** 클라이언트가 이미 끊긴 경우 IOException은 무시 (SSE 흔한 케이스) */
    private static void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.debug("[Place/SSE] 이벤트 전송 실패(클라이언트 종료 추정): {}", e.getMessage());
        }
    }

    /**
     * POST /api/places/filter
     * 프론트(Kakao JS SDK)가 수집한 장소 목록을 받아 Naver 블로그 필터 + finalScore 계산
     *
     * 요청 바디: { "places": [ { id, name, categoryCode, categoryName, lat, lng, address, rank }, ... ] }
     */
    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> filter(
            @RequestBody PlaceFilterRequest request) {

        if (request.getPlaces() == null || request.getPlaces().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("places 목록이 비어있습니다."));
        }

        List<PlaceCandidate> candidates = placeFilterService.filterRaw(request.getPlaces());

        List<Map<String, Object>> result = candidates.stream()
                .map(PlaceSuggestController::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/places/regions/trend?region=부산
     * DataLab으로 서브지역 인기 순위 조회 (지역 선택 화면용)
     */
    @GetMapping("/regions/trend")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> regionTrend(
            @RequestParam String region) {

        List<String> subRegions = RegionSeedData.get(region);
        if (subRegions.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("지원하지 않는 지역: " + region));
        }

        List<String> ranked = placeFilterService.getNaverDataLabService().rankByTrend(subRegions);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            result.add(Map.of("rank", i + 1, "subRegion", ranked.get(i)));
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 지원 지역명 목록
     */
    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<String>>> regions() {
        List<String> list = RegionSeedData.regions().stream().sorted().collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /** PlaceCandidate → 응답 Map (null 안전 — TourAPI 후보는 kakaoPlaceId 등이 null) */
    private static Map<String, Object> toMap(PlaceCandidate c) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id",           c.getKakaoPlaceId() != null ? c.getKakaoPlaceId() : "");
        m.put("name",         c.getName() != null ? c.getName() : "");
        m.put("category",     c.getCategoryCode() != null ? c.getCategoryCode() : "");
        m.put("subCategory",  c.getSubCategory() != null ? c.getSubCategory() : "sight");
        m.put("categoryName", c.getCategoryName() != null ? c.getCategoryName() : "");
        m.put("lat",          c.getLat());
        m.put("lng",          c.getLng());
        m.put("address",      c.getAddress() != null ? c.getAddress() : "");
        m.put("trendScore",   c.getTrendScore());
        m.put("finalScore",   c.getFinalScore());
        m.put("categoryPercentile", c.getCategoryPercentile());
        m.put("stayMinutes",  c.getDefaultStayMinutes());
        return m;
    }
}
