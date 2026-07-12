package com.gadang.user;

public record UpdateProfileRequest(
        String nickname,
        String password
) {
}
