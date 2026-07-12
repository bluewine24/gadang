package com.gadang.algorithm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gadang.course.dto.CourseRequest.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor   // JSON 역직렬화(L2 캐시 복원)용
@AllArgsConstructor
public class PlaceCandidate {

    private String kakaoPlaceId;
    private String name;
    private String categoryCode;
    private String categoryName;
    /** 프론트 7분류 (sight/park/photo/culture/cafe/food/shop) — categoryCode보다 세분화 */
    private String subCategory;
    private ActivityType activityType;

    private double lat;
    private double lng;
    private String address;

    private int distanceMeters;
    private double trendScore;    // DataLab 점수 0~100, 없으면 0
    private double kakaoAccuracy; // Kakao accuracy 순위 기반 0~1

    private double finalScore;    // 최종 종합 점수

    /** 같은 subCategory 내 인기 백분위 (0=최고 인기 ~ 100=최하위). 카테고리마다 검색량 스케일이
     *  달라(음식점≫공원) 전역 기준 대신 카테고리별로 계산 — 상위 N% 필터링에 사용 */
    private double categoryPercentile;

    // 입장료
    private int admissionFee;
    private boolean feeConfirmed; // true=확정, false=추정

    // 기본 체류 시간 (분) - 카테고리별 기본값, 사용자가 조정 가능
    private int defaultStayMinutes;

    @JsonIgnore
    public boolean isTouristOrCultural() {
        return "AT4".equals(categoryCode) || "CT1".equals(categoryCode);
    }
}
