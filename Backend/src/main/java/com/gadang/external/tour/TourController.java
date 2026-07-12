package com.gadang.external.tour;

import com.gadang.common.response.ApiResponse;
import com.gadang.external.tour.dto.TourDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tour")
@RequiredArgsConstructor
public class TourController {

    private final TourApiService tourApiService;

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<TourDetailDto>> detail(
            @RequestParam String name,
            @RequestParam(defaultValue = "sight") String cat) {
        return tourApiService.fetchDetail(name, cat)
                .map(d -> ResponseEntity.ok(ApiResponse.ok(d)))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }
}
