package com.gadang.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gadang.common.exception.GadangException;
import com.gadang.security.JwtProvider;
import com.gadang.user.User;
import com.gadang.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginReturnsTokenWhenPasswordMatches() {
        User user = user("user@test.com", "encoded", "USER");
        when(userMapper.findByEmail("user@test.com")).thenReturn(user);
        when(passwordEncoder.matches("raw", "encoded")).thenReturn(true);
        when(jwtProvider.createToken(1L, "user@test.com", "USER")).thenReturn("token");

        AuthResponse response = authService.login(new AuthRequest("user@test.com", "raw"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().email()).isEqualTo("user@test.com");
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(userMapper.findByEmail("user@test.com")).thenReturn(user("user@test.com", "encoded", "USER"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest("user@test.com", "raw", "tester")))
                .isInstanceOf(GadangException.class)
                .hasMessage("이미 가입된 이메일입니다.");
    }

    @Test
    void signupCreatesUserRoleAccount() {
        when(userMapper.findByEmail("new@test.com")).thenReturn(null);
        when(passwordEncoder.encode("raw")).thenReturn("encoded");
        when(jwtProvider.createToken(1L, "new@test.com", "USER")).thenReturn("token");
        when(userMapper.findById(1L)).thenReturn(user("new@test.com", "encoded", "USER"));
        org.mockito.Mockito.doAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setUserId(1L);
            return null;
        }).when(userMapper).insert(any(User.class));

        AuthResponse response = authService.signup(new SignupRequest("new@test.com", "raw", "newbie"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().role()).isEqualTo("USER");
    }

    private User user(String email, String password, String role) {
        User user = new User();
        user.setUserId(1L);
        user.setEmail(email);
        user.setPassword(password);
        user.setNickname("tester");
        user.setRole(role);
        return user;
    }
}
