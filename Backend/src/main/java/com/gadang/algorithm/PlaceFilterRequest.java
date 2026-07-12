package com.gadang.algorithm;

import lombok.Data;
import java.util.List;

/**
 * POST /api/places/filter 요청 바디
 *
 * 프론트(카카오 JS SDK)에서 수집한 장소 목록을 받아
 * Naver 블로그 count 필터 + finalScore 계산 후 반환
 */
@Data
public class PlaceFilterRequest {

    /** 카카오에서 수집한 장소 목록 */
    private List<RawPlace> places;

    @Data
    public static class RawPlace {
        private String id;            // Kakao place id
        private String name;          // 장소명 (Naver 블로그 검색에 사용)
        private String categoryCode;  // AT4 / CT1 / FD6 / CE7
        private String categoryName;
        private double lat;
        private double lng;
        private String address;
        private int rank;             // Kakao accuracy 순위 (1-based)
    }
}
