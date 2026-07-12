package com.gadang.external.odsay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Odsay LAB 대중교통 경로 검색 서비스
 * GET /searchPubTransPathT → 출발지·목적지 좌표 기반 실제 소요시간 + 요금 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdsayTransitService {

    @Qualifier("odsayRestClient")
    private final RestClient odsayRestClient;

    @Value("${odsay.api.key}")
    private String apiKey;

    /** 캐시 키: "lat1,lng1→lat2,lng2" */
    @Cacheable(value = "odsayRoute", key = "#fromLat + ',' + #fromLng + '->' + #toLat + ',' + #toLng")
    public int[] getTransit(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            Map<?, ?> resp = odsayRestClient.get()
                    .uri(u -> u.path("/searchPubTransPathT")
                            .queryParam("SX", fromLng)
                            .queryParam("SY", fromLat)
                            .queryParam("EX", toLng)
                            .queryParam("EY", toLat)
                            .queryParam("OPT", 0)
                            .queryParam("SearchType", 0)
                            .queryParam("SearchPathType", 0)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (resp == null) return null;

            Map<?, ?> result = (Map<?, ?>) resp.get("result");
            if (result == null) return null;

            var paths = (java.util.List<?>) result.get("path");
            if (paths == null || paths.isEmpty()) return null;

            // 첫 번째 경로(시간 최적) 사용
            Map<?, ?> first = (Map<?, ?>) paths.get(0);
            Map<?, ?> info = (Map<?, ?>) first.get("info");
            if (info == null) return null;

            int payment = ((Number) info.get("payment")).intValue();

            // totalTime은 배차 대기 포함 → subPath sectionTime 합산으로 순수 이동시간만 사용
            int totalMin = 0;
            List<?> subPaths = (List<?>) first.get("subPath");
            if (subPaths != null) {
                for (Object sp : subPaths) {
                    Map<?, ?> sub = (Map<?, ?>) sp;
                    Number t = (Number) sub.get("sectionTime");
                    if (t != null) totalMin += t.intValue();
                }
            }
            if (totalMin == 0) totalMin = ((Number) info.get("totalTime")).intValue();

            log.debug("[Odsay] ({},{})→({},{}) {}분(이동) {}원", fromLat, fromLng, toLat, toLng, totalMin, payment);
            return new int[]{ totalMin * 2, payment * 2 }; // 왕복

        } catch (Exception e) {
            log.warn("[Odsay] 경로 조회 실패 ({},{})→({},{}): {}", fromLat, fromLng, toLat, toLng, e.getMessage());
            return null; // null 이면 호출부에서 Haversine fallback
        }
    }
}
