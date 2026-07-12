package com.gadang.trip;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 확정 여행 일정 (TRIP_PLAN). 코스 타임라인은 course_json 스냅샷으로 보관. */
@Data
public class TripPlan {
    private Long tripId;
    private Long userId;
    private Long regionId;
    private String title;
    private LocalDate tripDate;
    private String startPoint;
    private String endPoint;
    private LocalTime departureTime;
    private LocalTime returnTime;
    private Integer budgetGuide;
    private int totalCost;
    private int foodCostEst;
    private String courseJson;
    private LocalDateTime createdAt;
}
