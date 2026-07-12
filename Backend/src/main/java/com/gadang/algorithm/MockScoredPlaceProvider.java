package com.gadang.algorithm;

import com.gadang.external.kakao.KakaoPlaceDto;
import com.gadang.external.kakao.KakaoPlacesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 코스 엔진 개발/폴백용 임시 점수 Provider.
 *
 * Kakao 실장소를 그대로 쓰되 점수만 임시 계산 — Naver 블로그/DataLab 호출이 없어
 * rate limit 딜레이 없이 즉시 응답한다.
 *
 * 운영 기본은 RealScoredPlaceProvider(@Primary). 이 빈은 이름 지정 주입 시에만 사용.
 */
@Slf4j
@Service("mockScoredPlaceProvider")
@RequiredArgsConstructor
public class MockScoredPlaceProvider implements ScoredPlaceProvider {

    private final KakaoPlacesService kakaoPlacesService;

    private static final List<String> DEFAULT_CATEGORIES = List.of("AT4", "CT1", "FD6", "CE7");

    @Override
    public List<PlaceCandidate> getScoredPlaces(double lat, double lng,
                                                int radiusMeters, List<String> categories) {
        List<String> cats = (categories == null || categories.isEmpty())
                ? DEFAULT_CATEGORIES : categories;

        Set<String> seenIds = new HashSet<>();
        List<PlaceCandidate> result = new ArrayList<>();

        for (String cat : cats) {
            for (KakaoPlaceDto p : kakaoPlacesService.searchByCategory(lat, lng, radiusMeters, cat)) {
                if (!seenIds.add(p.getId())) continue;

                int distance = parseDistance(p.getDistance());
                double score = mockScore(p.getId(), distance, radiusMeters);
                int stay = KakaoPlacesService.DEFAULT_STAY_MINUTES.getOrDefault(cat, 60);

                result.add(PlaceCandidate.builder()
                        .kakaoPlaceId(p.getId())
                        .name(p.getPlaceName())
                        .categoryCode(cat)
                        .categoryName(p.getCategoryGroupName())
                        .lat(p.getLat())
                        .lng(p.getLng())
                        .address(p.getAddressName())
                        .distanceMeters(distance)
                        .trendScore(score)              // 임시값임을 finalScore와 동일하게 유지
                        .kakaoAccuracy(1.0 / Math.max(1, p.getRank()))
                        .finalScore(score)
                        .admissionFee(estimateFee(cat))
                        .feeConfirmed(false)
                        .defaultStayMinutes(stay)
                        .build());
            }
        }

        result.sort(Comparator.comparingDouble(PlaceCandidate::getFinalScore).reversed());
        log.debug("[MockScore] ({}, {}) r={}m → {}개", lat, lng, radiusMeters, result.size());
        return result;
    }

    /** 거리 비례 기본 점수(0~80) + 장소ID 해시 지터(0~19) — 결정적(deterministic) */
    private static double mockScore(String placeId, int distanceMeters, int radiusMeters) {
        double distScore = 80.0 * (1.0 - Math.min(1.0, (double) distanceMeters / radiusMeters));
        int jitter = Math.abs(placeId.hashCode()) % 20;
        return Math.round((distScore + jitter) * 10) / 10.0;
    }

    /** 카테고리별 입장료 추정 (원) — 관광/문화만, 식비·카페비는 코스 엔진에서 별도 처리 */
    private static int estimateFee(String categoryCode) {
        return switch (categoryCode) {
            case "AT4" -> 3_000;
            case "CT1" -> 5_000;
            default    -> 0;
        };
    }

    private static int parseDistance(String distance) {
        try {
            return Integer.parseInt(distance);
        } catch (Exception e) {
            return 0;
        }
    }
}
