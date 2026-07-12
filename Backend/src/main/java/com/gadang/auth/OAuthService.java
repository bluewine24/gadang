package com.gadang.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadang.common.exception.GadangException;
import com.gadang.security.JwtProvider;
import com.gadang.user.User;
import com.gadang.user.UserMapper;
import com.gadang.user.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * 네이버/카카오 OAuth2 소셜 로그인.
 * 코드 교환 → 프로필 조회 → 회원 찾기(또는 생성) → JWT 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserMapper     userMapper;
    private final JwtProvider    jwtProvider;
    private final ObjectMapper   objectMapper;

    @Value("${naver.client-id}")      private String naverClientId;
    @Value("${naver.client-secret}")  private String naverClientSecret;
    @Value("${kakao.api.key}")        private String kakaoRestApiKey;
    @Value("${kakao.client-secret:}") private String kakaoClientSecret;

    private static final String NAVER_TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String NAVER_PROFILE_URL = "https://openapi.naver.com/v1/nid/me";
    private static final String KAKAO_TOKEN_URL   = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_PROFILE_URL = "https://kapi.kakao.com/v2/user/me";

    // ── 네이버 ─────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse loginWithNaver(String code, String redirectUri) {
        try {
            String tokenJson = RestClient.create().get()
                    .uri(NAVER_TOKEN_URL + "?grant_type=authorization_code"
                            + "&client_id=" + naverClientId
                            + "&client_secret=" + naverClientSecret
                            + "&code=" + code
                            + "&redirect_uri=" + redirectUri)
                    .retrieve().body(String.class);

            JsonNode tokenNode = objectMapper.readTree(tokenJson);
            String accessToken = tokenNode.path("access_token").asText();
            if (accessToken.isBlank()) throw GadangException.badRequest("네이버 토큰 발급 실패");

            String profileJson = RestClient.create().get()
                    .uri(NAVER_PROFILE_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);

            JsonNode profile = objectMapper.readTree(profileJson).path("response");
            String socialId = profile.path("id").asText();
            String email    = profile.path("email").asText(null);
            String nickname = profile.path("nickname").asText(profile.path("name").asText("네이버사용자"));

            return issueToken(findOrCreate("naver", socialId, email, nickname));
        } catch (GadangException e) {
            throw e;
        } catch (Exception e) {
            log.error("Naver OAuth error", e);
            throw GadangException.badRequest("네이버 로그인 처리 중 오류가 발생했습니다.");
        }
    }

    // ── 카카오 ─────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse loginWithKakao(String code, String redirectUri) {
        try {
            String formBody = "grant_type=authorization_code"
                    + "&client_id=" + kakaoRestApiKey
                    + "&redirect_uri=" + redirectUri
                    + "&code=" + code
                    + (kakaoClientSecret != null && !kakaoClientSecret.isBlank()
                       ? "&client_secret=" + kakaoClientSecret : "");

            log.info("[Kakao] token request — client_id={}, redirect_uri={}, client_secret={}",
                    kakaoRestApiKey, redirectUri,
                    (kakaoClientSecret != null && !kakaoClientSecret.isBlank()) ? "SET" : "EMPTY");

            String tokenJson = RestClient.create().post()
                    .uri(KAKAO_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formBody)
                    .retrieve().body(String.class);

            JsonNode tokenNode = objectMapper.readTree(tokenJson);
            String accessToken = tokenNode.path("access_token").asText();
            if (accessToken.isBlank()) throw GadangException.badRequest("카카오 토큰 발급 실패");

            String profileJson = RestClient.create().get()
                    .uri(KAKAO_PROFILE_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);

            JsonNode root      = objectMapper.readTree(profileJson);
            String   socialId  = root.path("id").asText();
            JsonNode account   = root.path("kakao_account");
            String   email     = account.path("email").asText(null);
            String   nickname  = account.path("profile").path("nickname").asText("카카오사용자");

            return issueToken(findOrCreate("kakao", socialId, email, nickname));
        } catch (GadangException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao OAuth error", e);
            throw GadangException.badRequest("카카오 로그인 처리 중 오류가 발생했습니다.");
        }
    }

    // ── 공통 ─────────────────────────────────────────────────────────

    private User findOrCreate(String provider, String socialId, String email, String nickname) {
        // 소셜 ID로 먼저 찾기
        User user = userMapper.findBySocialId(provider, socialId);
        if (user != null) return user;

        // 이메일로 기존 계정 찾기 (이메일 로그인 계정과 연동)
        if (email != null && !email.isBlank()) {
            user = userMapper.findByEmail(email);
            if (user != null) {
                // 소셜 ID 연동 (UPDATE는 별도 mapper가 없으므로 그냥 insert 건너뜀)
                return user;
            }
        }

        // 신규 소셜 회원 생성
        String finalEmail = (email != null && !email.isBlank())
                ? email
                : provider + "_" + socialId + "@social.gadang.local";

        User newUser = new User();
        newUser.setEmail(finalEmail);
        newUser.setPassword(null);                     // 소셜 로그인은 패스워드 없음
        newUser.setNickname(nickname);
        newUser.setRole("USER");
        newUser.setProvider(provider);
        newUser.setSocialId(socialId);
        userMapper.insert(newUser);
        return userMapper.findById(newUser.getUserId());
    }

    private AuthResponse issueToken(User user) {
        String jwt = jwtProvider.createToken(user.getUserId(), user.getEmail(), user.getRole());
        return new AuthResponse(jwt, UserSummaryResponse.from(user));
    }
}
