package com.gadang.mypage;

import java.time.LocalDate;
import java.time.LocalTime;

public record TripSummaryResponse(
        Long tripId,
        String title,
        LocalDate tripDate,
        String startPoint,
        String endPoint,
        LocalTime departureTime,
        LocalTime returnTime,
        Integer totalCost,
        Integer foodCostEst
) {
}
