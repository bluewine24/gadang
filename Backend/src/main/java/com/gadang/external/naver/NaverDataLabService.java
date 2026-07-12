package com.gadang.external.naver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 네이버 DataLab 검색어 트렌드 API — 서브지역 인기도 비교
 *
 * 용도: "부산"처럼 광역 지역이 입력됐을 때
 *       미리 정의된 서브지역(해운대, 광안리, ...) 중 검색량 Top-N 선별
 *
 * API: POST https://openapi.naver.com/v1/datalab/search
 *   - 1회 호출당 최대 5개 키워드 그룹
 *   - ratio: 가장 높은 기간을 100으로 정규화한 상대 검색량
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverDataLabService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Qualifier("naverRestClient")
    private final RestClient naverRestClient;

    private final com.gadang.external.transit.ExternalCacheMapper cacheMapper;

    /** L2(DB) 신선 기준 — 트렌드는 하루 단위면 충분 */
    private static final java.time.Duration TREND_TTL = java.time.Duration.ofHours(24);

    /** 배치 간 스케일 통일용 공통 기준 키워드 — 모든 배치에 포함시켜 이 값 대비 비율로 환산 */
    private static final String ANCHOR = "서울";

    /**
     * 서브지역 목록 → 최근 3개월 평균 검색 비율 Map (서울=100 기준 단일 스케일)
     *
     * DataLab ratio는 "요청 내 최고치=100"인 상대값이라 배치를 나누면 배치끼리 비교 불가.
     * → 모든 배치에 앵커(서울)를 끼워 넣고 (지역 ratio / 그 배치의 서울 ratio) × 100 으로 환산.
     *
     * @return { "여수" → 31.2, "순천" → 18.4, ... } (서울=100 기준)
     */
    @Cacheable(value = "datalabTrend", key = "#subRegions.toString()")
    public Map<String, Double> getAverageRatios(List<String> subRegions) {
        Map<String, Double> result = new LinkedHashMap<>();
        String endDate   = LocalDate.now().withDayOfMonth(1).minusMonths(1).format(FMT);
        String startDate = LocalDate.now().withDayOfMonth(1).minusMonths(3).format(FMT);

        List<String> others = subRegions.stream()
                .filter(r -> !ANCHOR.equals(r))
                .collect(Collectors.toList());

        // L2: DB에 신선한 점수가 있으면 DataLab 호출 없이 사용
        java.time.LocalDateTime fresh = java.time.LocalDateTime.now().minus(TREND_TTL);
        List<String> missing = new ArrayList<>();
        for (String region : others) {
            Double cached = null;
            try { cached = cacheMapper.findTrend(region, fresh); } catch (Exception ignored) {}
            if (cached != null) result.put(region, cached);
            else missing.add(region);
        }
        if (!missing.isEmpty()) log.info("[DataLab] L2 적중 {}건 / 신규 조회 {}건", result.size(), missing.size());

        // L3: 미적중분만 앵커 배치 조회 (배치당 4개 + 앵커, 앵커 대비 비율로 스케일 통일)
        for (List<String> batch : partition(missing, 4)) {
            List<String> withAnchor = new ArrayList<>(batch);
            withAnchor.add(ANCHOR);
            Map<String, Double> partial = fetchBatch(withAnchor, startDate, endDate);

            double anchorRatio = partial.getOrDefault(ANCHOR, 0.0);
            for (String region : batch) {
                double raw = partial.getOrDefault(region, 0.0);
                double scaled = anchorRatio > 0 ? raw / anchorRatio * 100.0 : raw;
                result.put(region, scaled);
                try { cacheMapper.upsertTrend(region, scaled); } catch (Exception ignored) {}
            }
        }
        if (subRegions.contains(ANCHOR)) result.put(ANCHOR, 100.0);

        // 앵커 대비 100을 넘을 수 있으므로 (서울보다 여행 검색이 많은 지역)
        // 전체 결과의 최대값 = 100 으로 재정규화해 표시 스케일 고정
        double max = result.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max > 0) {
            result.replaceAll((k, v) -> Math.round(v / max * 1000) / 10.0);
        }
        return result;
    }

    /**
     * 서브지역 목록 → 인기 순위 정렬 (높은 순)
     */
    public List<String> rankByTrend(List<String> subRegions) {
        Map<String, Double> ratios = getAverageRatios(subRegions);
        return ratios.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Double> fetchBatch(List<String> regions, String startDate, String endDate) {
        Map<String, Double> result = new LinkedHashMap<>();

        List<Map<String, Object>> keywordGroups = regions.stream()
                .map(r -> {
                    Map<String, Object> g = new HashMap<>();
                    g.put("groupName", r);
                    // 지역명 단독은 제외 — 뉴스·날씨·부동산 등 비여행 검색이 섞여 화제성 스파이크에 오염됨
                    g.put("keywords", List.of(r + " 여행", r + " 당일치기", r + " 가볼만한곳"));
                    return g;
                })
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("startDate", startDate);
        requestBody.put("endDate", endDate);
        requestBody.put("timeUnit", "month");
        requestBody.put("keywordGroups", keywordGroups);

        try {
            Map<?, ?> body = naverRestClient.post()
                    .uri("/datalab/search")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (body == null) return result;
            List<?> results = (List<?>) body.get("results");
            if (results == null) return result;

            for (Object item : results) {
                Map<?, ?> entry = (Map<?, ?>) item;
                String title = String.valueOf(entry.get("title"));
                List<?> data = (List<?>) entry.get("data");
                if (data == null || data.isEmpty()) {
                    result.put(title, 0.0);
                    continue;
                }
                double avg = data.stream()
                        .mapToDouble(d -> {
                            Object ratio = ((Map<?, ?>) d).get("ratio");
                            return ratio instanceof Number ? ((Number) ratio).doubleValue() : 0.0;
                        })
                        .average().orElse(0.0);
                result.put(title, avg);
            }

        } catch (Exception e) {
            log.warn("DataLab 호출 실패 {}: {}", regions, e.getMessage());
            regions.forEach(r -> result.put(r, 0.0));
        }
        return result;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }
}
