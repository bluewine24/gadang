package com.gadang.auth;

public record SignupRequest(
        String email,
        String password,
        String nickname
) {
}
