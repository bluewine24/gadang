package com.gadang.auth;

import com.gadang.user.UserSummaryResponse;

public record AuthResponse(
        String token,
        UserSummaryResponse user
) {
}
