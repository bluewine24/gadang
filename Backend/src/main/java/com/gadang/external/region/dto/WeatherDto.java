package com.gadang.external.region.dto;

import java.util.List;

public record WeatherDto(
        String emoji,
        String desc,
        String temperature,
        String feelsLike,
        String humidity,
        String windSpeed,
        int precipProb,
        List<DailyForecastDto> forecast
) {}
