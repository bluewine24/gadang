package com.gadang.admin;

import com.gadang.common.response.ApiResponse;
import com.gadang.common.response.PageResponse;
import com.gadang.user.UserSummaryResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminService adminService;

    public AdminUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserSummaryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(adminService.users(page, size, q));
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<UserSummaryResponse> updateRole(@PathVariable Long userId, @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok("회원 권한이 수정되었습니다.", adminService.updateRole(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> delete(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ApiResponse.ok("회원이 삭제되었습니다.", null);
    }
}
