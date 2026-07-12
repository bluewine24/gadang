package com.gadang.external.region;

import com.gadang.common.response.ApiResponse;
import com.gadang.external.kakao.KakaoPlacesService;
import com.gadang.external.region.dto.RegionSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionSearchController {

    private final RegionSearchService regionSearchService;
    private final KakaoPlacesService  kakaoPlacesService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RegionSearchResult>>> search(
            @RequestParam(defaultValue = "서울역") String from,
            @RequestParam(defaultValue = "08:00") String dep,
            @RequestParam(defaultValue = "20:00") String arr,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {

        List<RegionSearchResult> results = regionSearchService.search(from, lat, lng, dep, arr);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /** GPS 좌표 → 행정구역명 (프론트 위치 표시용) */
    @GetMapping("/geocode")
    public ResponseEntity<ApiResponse<Map<String, String>>> geocode(
            @RequestParam double lat,
            @RequestParam double lng) {

        String name = kakaoPlacesService.coordToRegionName(lat, lng);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("name", name != null ? name : "")));
    }
}
