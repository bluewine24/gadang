package com.gadang.notice;

import com.gadang.common.response.ApiResponse;
import com.gadang.common.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NoticeResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(noticeService.findPage(page, size));
    }

    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> detail(@PathVariable Long noticeId) {
        return ApiResponse.ok(noticeService.findById(noticeId));
    }
}
