package com.gadang.common.exception;

import lombok.Getter;

@Getter
public class GadangException extends RuntimeException {

    private final int status;

    public GadangException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static GadangException notFound(String message) {
        return new GadangException(404, message);
    }

    public static GadangException badRequest(String message) {
        return new GadangException(400, message);
    }

    public static GadangException unauthorized() {
        return new GadangException(401, "로그인이 필요합니다.");
    }

    public static GadangException forbidden() {
        return new GadangException(403, "권한이 없습니다.");
    }
}
