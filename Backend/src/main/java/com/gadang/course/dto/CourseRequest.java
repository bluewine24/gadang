package com.gadang.course.dto;

import lombok.Data;
import java.time.LocalTime;
import java.util.List;

@Data
public class CourseRequest {

    private String region;

    // 기본 조건
    private String startAddress;   // 직접 입력
    private Double startLat;       // GPS 자동 감지 시
    private Double startLng;
    private String destinationAddress;
    private Double destinationLat;
    private Double destinationLng;
    private String transportMode;
    private String transportFromHub;
    private String transportToHub;
    private Integer transportOneWayMin;
    private Integer transportFare;
    private Integer transportOriginToHubMin;
    private Integer transportOriginToHubFare;

    private LocalTime departureTime;
    private LocalTime returnTime;

    // 소프트 예산 가이드
    private Integer budgetGuide;

    // 고정 장소 (최대 6개)
    private List<FixedPlace> fixedPlaces;

    // UI 선호 카드의 실제 배치 순서. 있으면 초기 생성도 이 순서를 우선한다.
    private List<CourseRegenerateRequest.EditEntry> preferenceEntries;

    // 활동 유형 (미선택 시 전체)
    private List<ActivityType> activityTypes;

    // 카페
    private boolean cafeEnabled;
    private CafeType cafeType;
    private LocalTime cafeStartTime;
    private LocalTime cafeEndTime;
    private List<CafeConfig> cafeSlots;

    // 식사
    private MealConfig breakfast;
    private MealConfig lunch;
    private MealConfig dinner;

    // 트렌드 반영
    private boolean trendEnabled;

    public enum ActivityType {
        TOURIST_SPOT("AT4"),
        CULTURAL("CT1"),
        PARK("AT4"),        // AT4 + 키워드 "공원"
        PHOTO_SPOT("AT4"),  // AT4 + 키워드 "포토"
        SHOPPING("MT1"),
        NIGHT("AT4");       // AT4 + 키워드 "야경"

        public final String kakaoCode;
        ActivityType(String code) { this.kakaoCode = code; }
    }

    public enum CafeType { GENERAL, BAKERY, ROOFTOP, THEME }

    @Data
    public static class CafeConfig {
        private CafeType cafeType = CafeType.GENERAL;
        private LocalTime startTime;
        private LocalTime endTime;
    }

    @Data
    public static class FixedPlace {
        private String placeName;
        private Double lat;
        private Double lng;
        private LocalTime visitTime; // null 가능
        private LocalTime departTime;
    }

    @Data
    public static class MealConfig {
        private boolean enabled;
        private FoodType foodType = FoodType.ANY;
        private PriceLevel priceLevel = PriceLevel.MID;
        private LocalTime startTime;
        private LocalTime endTime;

        public enum FoodType {
            KOREAN, JAPANESE, CHINESE, WESTERN,
            BUNSIK, MEAT_GRILL, SEAFOOD, FASTFOOD,
            LOCAL_SPECIALTY, ANY
        }

        public enum PriceLevel {
            LOW(10_000), MID(15_000), HIGH(25_000);
            public final int estimatedCost;
            PriceLevel(int cost) { this.estimatedCost = cost; }
        }
    }
}
