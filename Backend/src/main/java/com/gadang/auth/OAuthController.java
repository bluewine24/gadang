package com.gadang.auth;

import com.gadang.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 소셜 로그인 코드 교환 API.
 *
 * 흐름:
 *   1. 프론트 → 각 소셜 OAuth 로그인 페이지 (직접 리다이렉트)
 *   2. 소셜 → 프론트 /auth/{provider}/callback?code=...
 *   3. 프론트 → POST /api/auth/{provider}/exchange { code, redirectUri }
 *   4. 백엔드 → 토큰 교환 → 사용자 찾기/생성 → JWT 반환
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    @PostMapping("/naver/exchange")
    public ApiResponse<AuthResponse> naverExchange(@RequestBody Map<String, String> body) {
        String code        = body.get("code");
        String redirectUri = body.get("redirectUri");
        return ApiResponse.ok("네이버 로그인 성공", oAuthService.loginWithNaver(code, redirectUri));
    }

    @PostMapping("/kakao/exchange")
    public ApiResponse<AuthResponse> kakaoExchange(@RequestBody Map<String, String> body) {
        String code        = body.get("code");
        String redirectUri = body.get("redirectUri");
        return ApiResponse.ok("카카오 로그인 성공", oAuthService.loginWithKakao(code, redirectUri));
    }
}
