package com.gadang.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseRegenerateRequest {

    private CourseRequest base;
    private List<EditEntry> entries;

    public enum EntryType {
        LOCKED_PLACE,
        SPECIFIC_PLACE,
        ACTIVITY_SLOT,
        CAFE_SLOT,
        LUNCH,
        DINNER
    }

    @Data
    public static class EditEntry {
        private String clientId;
        private EntryType type;

        private String pid;
        private String placeName;
        private Double lat;
        private Double lng;
        private String cat;
        private String role;
        private String meal;
        private Integer stayMinutes;
        private Integer fee;
        private String feeType;

        private CourseRequest.ActivityType activityType;
        private CourseRequest.CafeType cafeType;
        private CourseRequest.MealConfig.FoodType foodType;
        private CourseRequest.MealConfig.PriceLevel priceLevel;
    }
}
