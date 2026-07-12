package com.gadang.trip;

import com.gadang.common.response.ApiResponse;
import com.gadang.mypage.TripSummaryResponse;
import com.gadang.security.CurrentUser;
import com.gadang.trip.TripDtos.TripDetailResponse;
import com.gadang.trip.TripDtos.TripSaveRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 확정 여행 일정 (F126).
 * 코스 추천 결과를 '확정'하면 일정으로 저장하고, 일정 탭에서 목록·상세로 본다.
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> save(
            @AuthenticationPrincipal CurrentUser user,
            @RequestBody TripSaveRequest request) {
        Long tripId = tripService.save(user.userId(), request);
        return ApiResponse.ok("일정이 확정되었습니다.", Map.of("tripId", tripId));
    }

    @GetMapping
    public ApiResponse<List<TripSummaryResponse>> list(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(tripService.list(user.userId()));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> detail(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long tripId) {
        return ApiResponse.ok(tripService.get(user.userId(), tripId));
    }

    @DeleteMapping("/{tripId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long tripId) {
        tripService.delete(user.userId(), tripId);
        return ApiResponse.ok("일정이 삭제되었습니다.", null);
    }
}
