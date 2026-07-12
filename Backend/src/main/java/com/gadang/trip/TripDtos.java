package com.gadang.trip;

import com.gadang.course.dto.CourseResponse;

import java.time.LocalDate;
import java.time.LocalTime;

/** 트립 저장/조회 DTO 모음. */
public class TripDtos {

    private TripDtos() {}

    /** 확정 저장 요청 — 코스 추천 결과(course) 전체 + 제목 + 선택 여행일. */
    public record TripSaveRequest(String title, LocalDate tripDate, CourseResponse course) {}

    /** 확정 일정 상세 — 요약 + 코스 타임라인 스냅샷. */
    public record TripDetailResponse(
            Long tripId,
            String title,
            LocalDate tripDate,
            String startPoint,
            String endPoint,
            LocalTime departureTime,
            LocalTime returnTime,
            Integer budgetGuide,
            Integer totalCost,
            Integer foodCostEst,
            CourseResponse course
    ) {}
}
