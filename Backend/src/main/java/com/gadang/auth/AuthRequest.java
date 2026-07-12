package com.gadang.auth;

public record AuthRequest(
        String email,
        String password
) {
}
