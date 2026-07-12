package com.gadang.trip;

import lombok.Data;

@Data
public class TripRouteRow {
    private Long tripId;
    private Long fromItemId;
    private Long toItemId;
    private String transportType;
    private int durationMinutes;
    private int fare;
}
