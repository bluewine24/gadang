package com.gadang.user;

import com.gadang.common.exception.GadangException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User getUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw GadangException.notFound("회원을 찾을 수 없습니다.");
        }
        return user;
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        String nickname = requireText(request.nickname(), "닉네임을 입력해 주세요.");
        if (hasText(request.password())) {
            userMapper.updateNicknameAndPassword(userId, nickname, passwordEncoder.encode(request.password()));
        } else {
            userMapper.updateNickname(userId, nickname);
        }
        return getUser(userId);
    }

    @Transactional
    public void deleteProfile(Long userId) {
        userMapper.deleteById(userId);
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw GadangException.badRequest(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
