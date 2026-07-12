package com.gadang.external.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Kakao 장소 검색 API 응답 단일 문서
 */
@Data
public class KakaoPlaceDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("place_name")
    private String placeName;

    @JsonProperty("category_group_code")
    private String categoryGroupCode;

    @JsonProperty("category_group_name")
    private String categoryGroupName;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("address_name")
    private String addressName;

    @JsonProperty("road_address_name")
    private String roadAddressName;

    @JsonProperty("x")
    private String x;   // 경도(lng)

    @JsonProperty("y")
    private String y;   // 위도(lat)

    @JsonProperty("place_url")
    private String placeUrl;

    @JsonProperty("distance")
    private String distance;

    // --- 계산 필드 (API 응답 외) ---
    private int rank;          // accuracy 기준 순위 (1-based)
    private double accuracy;   // rank 기반 0~1 정규화 점수

    public double getLat() {
        try { return Double.parseDouble(y); } catch (Exception e) { return 0.0; }
    }

    public double getLng() {
        try { return Double.parseDouble(x); } catch (Exception e) { return 0.0; }
    }
}
