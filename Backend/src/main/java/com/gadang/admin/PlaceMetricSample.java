package com.gadang.admin;

public record PlaceMetricSample(
        Long placeId,
        String placeName,
        String categoryCode,
        String categoryName,
        Integer cost,
        Integer durationMin
) {
}
