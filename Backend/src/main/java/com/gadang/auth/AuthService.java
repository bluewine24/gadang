package com.gadang.auth;

import com.gadang.common.exception.GadangException;
import com.gadang.security.JwtProvider;
import com.gadang.user.User;
import com.gadang.user.UserMapper;
import com.gadang.user.UserSummaryResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = requireText(request.email(), "이메일을 입력해 주세요.");
        String password = requireText(request.password(), "비밀번호를 입력해 주세요.");
        String nickname = requireText(request.nickname(), "닉네임을 입력해 주세요.");
        if (userMapper.findByEmail(email) != null) {
            throw GadangException.badRequest("이미 가입된 이메일입니다.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole("USER");
        user.setProvider("local");
        userMapper.insert(user);

        return issueToken(userMapper.findById(user.getUserId()));
    }

    public AuthResponse login(AuthRequest request) {
        User user = userMapper.findByEmail(requireText(request.email(), "이메일을 입력해 주세요."));
        if (user == null || !passwordEncoder.matches(requireText(request.password(), "비밀번호를 입력해 주세요."), user.getPassword())) {
            throw GadangException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtProvider.createToken(user.getUserId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, UserSummaryResponse.from(user));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw GadangException.badRequest(message);
        }
        return value.trim();
    }
}
