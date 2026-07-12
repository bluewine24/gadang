package com.gadang.user;

import java.time.LocalDateTime;

public record UserSummaryResponse(
        Long userId,
        String email,
        String nickname,
        String role,
        LocalDateTime createdAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getCreatedAt());
    }
}
