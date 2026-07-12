package com.gadang.notice;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long noticeId,
        Long userId,
        String authorNickname,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getNoticeId(),
                notice.getUserId(),
                notice.getAuthorNickname(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}
