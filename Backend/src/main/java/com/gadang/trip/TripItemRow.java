package com.gadang.trip;

import lombok.Data;

import java.time.LocalTime;

@Data
public class TripItemRow {
    private Long itemId;
    private Long tripId;
    private Long placeId;
    private int visitOrder;
    private LocalTime arrivalTime;
    private int stayMinutes;
    private int admissionFee;
    private int foodCost;
}
