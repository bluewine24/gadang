package com.gadang.external.region.dto;

import java.util.List;

public record RegionInfoDto(
        WeatherDto weather,
        List<FestivalDto> festivals
) {}
