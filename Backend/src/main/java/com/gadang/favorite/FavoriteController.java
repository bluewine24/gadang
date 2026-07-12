package com.gadang.favorite;

import com.gadang.common.response.ApiResponse;
import com.gadang.common.response.PageResponse;
import com.gadang.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FavoritePlaceResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(favoriteService.findByUser(currentUser.userId(), page, size));
    }

    @PostMapping("/{placeId}")
    public ApiResponse<Void> add(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long placeId) {
        favoriteService.add(currentUser.userId(), placeId);
        return ApiResponse.ok("즐겨찾기에 추가되었습니다.", null);
    }

    @DeleteMapping("/{placeId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long placeId) {
        favoriteService.remove(currentUser.userId(), placeId);
        return ApiResponse.ok("즐겨찾기에서 삭제되었습니다.", null);
    }
}
