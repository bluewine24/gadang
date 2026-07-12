package com.gadang.common.exception;

import com.gadang.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GadangException.class)
    public ResponseEntity<ApiResponse<Void>> handleGadangException(GadangException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[500] {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResponseEntity
                .status(500)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
