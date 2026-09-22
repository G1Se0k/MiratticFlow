package com.mirattic.flow.auth.service;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.auth.dto.TokenResponse;
import com.mirattic.flow.auth.repository.RefreshTokenRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.security.JwtProvider;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** DB 없이 AuthService 의 규칙만 검증한다. */
class AuthServiceTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProvider jwtProvider = new JwtProvider("test-secret-key-for-unit-tests-32bytes-long!", 900_000, 1_209_600_000);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtProvider);
    }

    private User savedUser(String email, String rawPassword) {
        User user = User.create(email, passwordEncoder.encode(rawPassword), "테스터");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    @DisplayName("회원가입하면 비밀번호가 평문이 아닌 해시로 저장된다")
    void signup() {
        when(userRepository.existsByEmail("a@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        var response = authService.signup(new SignupRequest("a@test.com", "password123", "테스터"));

        assertThat(response.email()).isEqualTo("a@test.com");
        verify(userRepository).save(argThat(user -> !user.getPassword().equals("password123")));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_DUPLICATED")
    void signupDuplicated() {
        when(userRepository.existsByEmail("a@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("a@test.com", "password123", "테스터")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("로그인에 성공하면 토큰 두 개를 발급하고 refresh 를 저장한다")
    void login() {
        when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(savedUser("a@test.com", "password123")));

        TokenResponse tokens = authService.login(new LoginRequest("a@test.com", "password123"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LOGIN_FAILED")
    void loginWrongPassword() {
        when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(savedUser("a@test.com", "password123")));

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@test.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("없는 이메일도 LOGIN_FAILED 로 응답해 가입 여부를 노출하지 않는다")
    void loginUnknownEmail() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@test.com", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("연달아 발급해도 refresh token 은 서로 다르다 (rotation 이 성립하려면 필수)")
    void refreshTokensAreUnique() {
        JwtProvider jwtProvider = new JwtProvider("test-secret-key-for-unit-tests-32bytes-long!", 900_000, 1_209_600_000);

        String first = jwtProvider.createRefreshToken(1L, "a@test.com");
        String second = jwtProvider.createRefreshToken(1L, "a@test.com");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("DB 에 없는 refresh token 은 서명이 맞아도 거부한다")
    void reissueUnknownToken() {
        JwtProvider jwtProvider = new JwtProvider("test-secret-key-for-unit-tests-32bytes-long!", 900_000, 1_209_600_000);
        String refreshToken = jwtProvider.createRefreshToken(1L, "a@test.com");
        when(refreshTokenRepository.findByToken(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
