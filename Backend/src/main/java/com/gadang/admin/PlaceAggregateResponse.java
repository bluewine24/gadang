package com.gadang.admin;

public record PlaceAggregateResponse(
        Long placeId,
        String placeName,
        String categoryCode,
        String categoryName,
        long sampleCount,
        long costSampleCount,
        long durationSampleCount,
        int averageCost,
        int averageDurationMin,
        int minCost,
        int maxCost,
        int minDurationMin,
        int maxDurationMin
) {
}
