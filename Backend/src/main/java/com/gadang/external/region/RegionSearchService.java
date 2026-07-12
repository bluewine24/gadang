package com.gadang.external.region;

import com.gadang.algorithm.RegionSeedData;
import com.gadang.algorithm.RegionSeedData.RegionMeta;
import com.gadang.external.kakao.KakaoPlacesService;
import com.gadang.external.naver.NaverDataLabService;
import com.gadang.external.tour.TourApiService;
import com.gadang.external.region.dto.RegionSearchResult;
import com.gadang.external.region.dto.TransportOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 당일치기 지역 추천.
 *
 * 지역 후보는 고정 목록이 아니라 RegionDiscoveryService가
 * "출발지 근처 역·터미널에서 오늘 직통으로 갈 수 있는 곳"을 역으로 수집해 만든다.
 * 큐레이션 메타(RegionSeedData.REGION_META)는 있으면 소개문·태그 보강용으로만 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionSearchService {

    private final KakaoPlacesService     kakaoPlacesService;
    private final NaverDataLabService    naverDataLabService;
    private final RegionDiscoveryService regionDiscoveryService;
    private final TourApiService         tourApiService;

    private static final int    MIN_STAY_MIN  = 90;
    private static final double HOT_THRESHOLD = 60.0;

    public List<RegionSearchResult> search(String from, Double fromLat, Double fromLng,
                                           String depTime, String arrTime) {
        int windowMin = parseWindow(depTime, arrTime);

        // 1. 출발지 좌표
        double[] origin;
        if (fromLat != null && fromLng != null) {
            origin = new double[]{fromLat, fromLng};
        } else {
            String query = from.contains("GPS") || from.isBlank() ? "서울역" : from;
            origin = kakaoPlacesService.searchByKeyword(query)
                    .map(p -> new double[]{p.getLat(), p.getLng()})
                    .orElse(new double[]{37.5665, 126.9780});
        }

        // 2. 자기 지역 감지 — 좌표가 명시되면 from 문자열(기본값 "서울역")은 무시
        String fromText = (fromLat == null && from != null) ? from : "";
        String selfRegion = detectSelfRegion(fromText, origin);
        log.info("[지역검색] 출발지={} coord=({},{}) 자기지역={} 가용{}분",
                from, origin[0], origin[1], selfRegion, windowMin);

        // 3. 직통 교통 기반 지역 발견
        Map<String, List<TransportOption>> discovered = regionDiscoveryService.discover(origin);
        if (selfRegion != null) discovered.remove(selfRegion);

        // 4. 트렌드 (발견된 지역 + 자기 지역)
        List<String> regionNames = new ArrayList<>(discovered.keySet());
        List<String> trendNames = new ArrayList<>(regionNames);
        if (selfRegion != null) trendNames.add(selfRegion);
        Map<String, Double> trends = naverDataLabService.getAverageRatios(trendNames);

        // 5. 지역별 결과 생성
        List<RegionSearchResult> results = new ArrayList<>();

        if (selfRegion != null && RegionSeedData.REGION_META.containsKey(selfRegion)) {
            RegionMeta meta = RegionSeedData.REGION_META.get(selfRegion);
            double selfTrend = trends.getOrDefault(selfRegion, 0.0);
            results.add(RegionSearchResult.builder()
                    .id(selfRegion).name(selfRegion)
                    .sido(KakaoPlacesService.normalizeSido(meta.sido))
                    .transport("도보·대중교통")
                    .trend(Math.round(selfTrend * 10) / 10.0)
                    .roundTrip(0).fare(0)
                    .stay(windowMin)
                    .tags(meta.tags)
                    .blurb(meta.blurb + " (내 지역!)")
                    .hot(selfTrend >= HOT_THRESHOLD).self(true)
                    .options(List.of())
                    .images(List.of())
                    .build());
        }

        List<RegionSearchResult> destResults = regionNames.stream()
                .map(name -> {
                    List<TransportOption> options = discovered.get(name);
                    TransportOption best = options.get(0);
                    int roundTrip = best.getOneWayMin() * 2;
                    int fare      = best.getFare() * 2;

                    int stay = windowMin - roundTrip;
                    if (stay < MIN_STAY_MIN) return null;

                    RegionMeta meta = RegionSeedData.REGION_META.get(name);
                    double rawTrend = trends.getOrDefault(name, 0.0);

                    return RegionSearchResult.builder()
                            .id(name).name(name)
                            .sido(KakaoPlacesService.normalizeSido(
                                    meta != null ? meta.sido : regionDiscoveryService.sidoOf(name)))
                            .transport(best.getType())
                            .trend(Math.round(rawTrend * 10) / 10.0)
                            .roundTrip(roundTrip)
                            .fare(fare)
                            .stay(stay)
                            .tags(meta != null ? meta.tags : List.of())
                            .blurb(meta != null ? meta.blurb : autoBlurb(best))
                            .hot(rawTrend >= HOT_THRESHOLD)
                            .self(false)
                            .options(options)
                            .images(List.of())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(r -> -r.getTrend()))
                .collect(Collectors.toList());
        results.addAll(destResults);

        // 6. 이미지 병렬 로딩 (캐시 없으면 최초 1회만 느림)
        List<CompletableFuture<Void>> imgFutures = results.stream()
                .map(r -> CompletableFuture.runAsync(() ->
                        r.setImages(tourApiService.getRegionImages(r.getName()))))
                .collect(Collectors.toList());
        CompletableFuture.allOf(imgFutures.toArray(new CompletableFuture[0])).join();

        log.info("[지역검색] 결과 {}곳", results.size());
        return results;
    }

    /** 큐레이션 메타가 없는 동적 발견 지역의 한 줄 소개 */
    private static String autoBlurb(TransportOption best) {
        if (best.getDailyTrips() > 0) {
            return String.format("%s 직통 하루 %d회 — %s 출발",
                    best.getType(), best.getDailyTrips(), best.getFromHub());
        }
        return best.getType() + " 직통 — " + best.getFromHub() + " 출발";
    }

    // ──────────────────────────────────────────────────────────────

    private int parseWindow(String dep, String arr) {
        try {
            String[] d = dep.split(":"), a = arr.split(":");
            int dm = Integer.parseInt(d[0]) * 60 + Integer.parseInt(d[1]);
            int am = Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
            int diff = am - dm;
            return diff <= 0 ? diff + 1440 : diff;
        } catch (Exception e) { return 720; }
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String detectSelfRegion(String from, double[] coord) {
        for (String name : RegionSeedData.REGION_META.keySet()) {
            if (from.contains(name)) return name;
        }
        String closest = null;
        double minKm = Double.MAX_VALUE;
        for (Map.Entry<String, RegionMeta> e : RegionSeedData.REGION_META.entrySet()) {
            double km = haversineKm(coord[0], coord[1], e.getValue().lat, e.getValue().lng);
            if (km < minKm) { minKm = km; closest = e.getKey(); }
        }
        return (minKm <= 30 && closest != null) ? closest : null;
    }
}
