package com.gadang.external.region.dto;

public record DailyForecastDto(
        String date,       // "06-24" (MM-dd)
        String dow,        // "화" 요일 한 글자
        String emoji,
        String tempMax,
        String tempMin,
        int precipProb
) {}
