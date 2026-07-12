package com.gadang.ai.tool;

import com.gadang.ai.ChatActionContext;
import com.gadang.algorithm.PlaceCandidate;
import com.gadang.algorithm.PlaceFilterService;
import com.gadang.algorithm.ScoredPlaceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GadangTravelTools {

    private final PlaceFilterService placeFilterService;
    private final ScoredPlaceProvider scoredPlaceProvider;
    private final RestClient ragRestClient;

    public GadangTravelTools(PlaceFilterService placeFilterService,
                             ScoredPlaceProvider scoredPlaceProvider,
                             @Qualifier("ragRestClient") RestClient ragRestClient) {
        this.placeFilterService = placeFilterService;
        this.scoredPlaceProvider = scoredPlaceProvider;
        this.ragRestClient = ragRestClient;
    }

    public record SharedCourse(String region, String theme, String title,
                               String content, double score) {}

    @Tool(description = """
            다른 사용자가 공유한 여행 코스 후기 중에서 사용자의 취향과 상황에 자연어로 비슷한 글을 찾는다.
            '비슷한 코스 보여줘', '바다 보며 회 먹는 가성비 코스 글 찾아줘'처럼 공유 코스를 찾을 때 호출한다.
            FastAPI RAG 서버가 커뮤니티 글을 검색해 가장 가까운 후기를 반환한다.
            """)
    @SuppressWarnings("unchecked")
    public List<SharedCourse> searchSharedCourses(
            @ToolParam(description = "찾고 싶은 코스의 취향·상황 자연어 설명") String query,
            @ToolParam(description = "특정 지역으로 한정하려면 지역명, 아니면 null", required = false) String region) {
        log.info("[Tool] searchSharedCourses(RAG) query={} region={}", query, region);
        try {
            List<Map<String, Object>> hits = ragRestClient.post()
                    .uri("/search")
                    .body(Map.of("query", query == null ? "" : query,
                            "top_k", 3,
                            "region", region == null ? "" : region))
                    .retrieve()
                    .body(List.class);
            if (hits == null) return List.of();
            List<SharedCourse> courses = hits.stream()
                    .map(h -> new SharedCourse(
                            str(h.get("region")), str(h.get("theme")),
                            str(h.get("title")), str(h.get("content")),
                            h.get("score") instanceof Number n ? n.doubleValue() : 0.0))
                    .toList();
            String topRegion = courses.isEmpty() ? "" : courses.get(0).region();
            ChatActionContext.add("shared", "공유 코스 보러가기", "/community",
                    topRegion.isEmpty() ? Map.of() : Map.of("region", topRegion));
            return courses;
        } catch (Exception e) {
            log.warn("[Tool] RAG 서버 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    public record PlaceSuggestion(String name, String category, String address,
                                  double trend, int stayMin, double lat, double lng) {}

    @Tool(description = """
            특정 지역 안에서 가볼 만한 장소, 맛집, 카페, 문화시설을 추천한다.
            '부산 가볼 만한 곳', '전주 맛집 추천'처럼 지역 내 장소를 찾을 때 호출한다.
            트렌드와 블로그 언급량 기반 점수가 높은 장소를 반환한다.
            """)
    public List<PlaceSuggestion> recommendPlaces(
            @ToolParam(description = "장소를 찾을 지역명 예: 부산, 전주") String region,
            @ToolParam(description = "검색 반경(m). 기본 3000", required = false) Integer radiusMeters) {
        log.info("[Tool] recommendPlaces region={} radius={}", region, radiusMeters);
        int radius = radiusMeters == null ? 3000 : radiusMeters;
        double[] center = placeFilterService.resolveRegionCenter(region).orElse(null);
        if (center == null) {
            log.warn("[Tool] recommendPlaces 좌표 미발견: {}", region);
            return List.of();
        }
        List<PlaceCandidate> candidates =
                scoredPlaceProvider.getScoredPlaces(center[0], center[1], radius, null, region);
        List<PlaceCandidate> top = candidates.stream()
                .filter(c -> c.getCategoryPercentile() <= 45)
                .limit(6)
                .toList();

        for (PlaceCandidate c : top) {
            Map<String, String> q = new java.util.HashMap<>();
            q.put("region", region);
            q.put("place", c.getName() == null ? "" : c.getName());
            q.put("lat", String.valueOf(c.getLat()));
            q.put("lng", String.valueOf(c.getLng()));
            q.put("cat", c.getCategoryCode() == null ? "" : c.getCategoryCode());
            ChatActionContext.add("place", c.getName(), "/map", q);
        }

        return top.stream()
                .map(c -> new PlaceSuggestion(
                        c.getName(), c.getCategoryName(), c.getAddress(),
                        c.getTrendScore(), c.getDefaultStayMinutes(),
                        c.getLat(), c.getLng()))
                .toList();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
