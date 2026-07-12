package com.gadang.external.region;

import com.gadang.common.response.ApiResponse;
import com.gadang.external.region.dto.RegionInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/region")
@RequiredArgsConstructor
public class RegionInfoController {

    private final WeatherService weatherService;
    private final FestivalService festivalService;

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<RegionInfoDto>> info(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {

        var weather   = weatherService.fetchWeather(region, lat, lng).orElse(null);
        var festivals = festivalService.fetchFestivals(region);

        return ResponseEntity.ok(ApiResponse.ok(new RegionInfoDto(weather, festivals)));
    }
}
