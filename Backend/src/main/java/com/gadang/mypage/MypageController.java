package com.gadang.mypage;

import com.gadang.common.response.ApiResponse;
import com.gadang.common.response.PageResponse;
import com.gadang.community.PostResponse;
import com.gadang.favorite.FavoritePlaceResponse;
import com.gadang.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
public class MypageController {

    private final MypageService mypageService;

    public MypageController(MypageService mypageService) {
        this.mypageService = mypageService;
    }

    @GetMapping("/summary")
    public ApiResponse<MypageSummaryResponse> summary(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(mypageService.summary(currentUser.userId()));
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostResponse>> posts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(mypageService.posts(currentUser.userId(), page, size));
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResponse<FavoritePlaceResponse>> favorites(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(mypageService.favorites(currentUser.userId(), page, size));
    }

    @GetMapping("/trips")
    public ApiResponse<PageResponse<TripSummaryResponse>> trips(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(mypageService.trips(currentUser.userId(), page, size));
    }
}
