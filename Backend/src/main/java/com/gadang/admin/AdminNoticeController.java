package com.gadang.admin;

import com.gadang.common.response.ApiResponse;
import com.gadang.notice.NoticeRequest;
import com.gadang.notice.NoticeResponse;
import com.gadang.notice.NoticeService;
import com.gadang.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping
    public ApiResponse<NoticeResponse> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody NoticeRequest request) {
        return ApiResponse.ok("공지사항이 등록되었습니다.", noticeService.create(currentUser.userId(), request));
    }

    @PatchMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> update(@PathVariable Long noticeId, @RequestBody NoticeRequest request) {
        return ApiResponse.ok("공지사항이 수정되었습니다.", noticeService.update(noticeId, request));
    }

    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> delete(@PathVariable Long noticeId) {
        noticeService.delete(noticeId);
        return ApiResponse.ok("공지사항이 삭제되었습니다.", null);
    }
}
