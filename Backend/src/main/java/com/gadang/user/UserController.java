package com.gadang.user;

import com.gadang.common.response.ApiResponse;
import com.gadang.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserSummaryResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(UserSummaryResponse.from(userService.getUser(currentUser.userId())));
    }

    @PatchMapping("/me")
    public ApiResponse<UserSummaryResponse> updateMe(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("프로필이 수정되었습니다.", UserSummaryResponse.from(userService.updateProfile(currentUser.userId(), request)));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(@AuthenticationPrincipal CurrentUser currentUser) {
        userService.deleteProfile(currentUser.userId());
        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null);
    }
}
