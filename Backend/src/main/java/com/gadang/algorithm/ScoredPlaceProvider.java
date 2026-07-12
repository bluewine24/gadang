package com.gadang.algorithm;

import java.util.List;

/**
 * 코스 엔진(축 2)이 장소 후보를 받아가는 유일한 통로.
 *
 * 축 1(스코어링)과 축 2(코스 생성)의 경계 인터페이스 —
 * 코스 엔진은 이 인터페이스만 주입받고, 내부 구현(임시 점수 vs 실제 스코어링)은 몰라도 된다.
 *
 * 구현체:
 *   MockScoredPlaceProvider — Kakao 실장소 + 임시 점수. Naver 호출 없어 즉시 응답 (현재 @Primary)
 *   RealScoredPlaceProvider — PlaceFilterService 위임 (트렌드+블로그 스코어링)
 *
 * 코스 시작·종료 지점은 RegionSeedData.REGION_META.get(지역명)의
 * hubLat / hubLng / hubName (역·터미널·공항)을 사용한다.
 */
public interface ScoredPlaceProvider {

    /**
     * 중심 좌표 반경 내 장소 후보를 finalScore 내림차순으로 반환.
     *
     * @param lat          중심 위도 (보통 RegionMeta.hubLat 또는 지역 중심)
     * @param lng          중심 경도
     * @param radiusMeters 반경
     * @param categories   Kakao 카테고리 코드 (AT4/CT1/FD6/CE7), null이면 전체
     */
    List<PlaceCandidate> getScoredPlaces(double lat, double lng,
                                         int radiusMeters, List<String> categories);

    /**
     * 호출부가 이미 지역명을 알고 있을 때(지역명 검색) 함께 전달하는 오버로드.
     * 좌표 역지오코딩이 실패해도 서브지역 Seed 매칭이 깨지지 않도록 한다.
     * 기본 구현은 힌트를 무시하고 좌표 기반 메서드로 위임한다.
     */
    default List<PlaceCandidate> getScoredPlaces(double lat, double lng,
                                                  int radiusMeters, List<String> categories,
                                                  String regionHint) {
        return getScoredPlaces(lat, lng, radiusMeters, categories);
    }

    /**
     * SSE 스트리밍용 — 구역(zone)을 찾는 즉시 {@code onPartial}로 미정렬·미채점 원시 후보를
     * 통지하고(프론트가 마커를 바로 찍을 수 있게), 전체 수집·블로그 채점이 끝나면
     * {@code onComplete}로 최종 결과(정렬·percentile 포함)를 통지한다.
     * 기본 구현은 스트리밍을 지원하지 않는 Provider(Mock 등)를 위한 폴백 — 중간 통지 없이 바로 완료.
     */
    default void streamScoredPlaces(double lat, double lng, int radiusMeters,
                                     List<String> categories, String regionHint,
                                     java.util.function.Consumer<List<PlaceCandidate>> onPartial,
                                     java.util.function.Consumer<List<PlaceCandidate>> onComplete) {
        onComplete.accept(getScoredPlaces(lat, lng, radiusMeters, categories, regionHint));
    }
}
