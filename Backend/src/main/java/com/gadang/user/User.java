package com.gadang.user;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class User {
    private Long userId;
    private String email;
    private String password;
    private String nickname;
    private String role;
    private String provider;   // local / naver / kakao
    private String socialId;   // 소셜 제공자의 사용자 ID
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
