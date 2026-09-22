package com.mirattic.flow.auth.service;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.auth.dto.TokenResponse;
import com.mirattic.flow.auth.entity.RefreshToken;
import com.mirattic.flow.auth.repository.RefreshTokenRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.security.JwtProvider;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.name());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 에러로 응답한다.
                // 다르게 응답하면 "이 이메일은 가입되어 있다"는 정보가 새어 나간다.
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return issueTokens(user);
    }

    /**
     * Access Token 이 만료되었을 때 Refresh Token 으로 새 토큰 쌍을 받는다.
     * 쓴 refresh 는 즉시 폐기하고 새로 발급한다(rotation) → 탈취된 토큰의 수명을 짧게 만든다.
     */
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                // 서명은 멀쩡해도 DB 에 없으면 이미 로그아웃했거나 회전된 토큰이다.
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (saved.isExpired()) {
            refreshTokenRepository.delete(saved);
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }
        User user = saved.getUser();
        refreshTokenRepository.delete(saved);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());
        refreshTokenRepository.save(RefreshToken.issue(refreshToken, user, jwtProvider.refreshTokenExpiresAt()));
        // ponytail: 만료된 refresh 행은 쌓인 채로 둔다. 양이 문제되면 스케줄러로 정리.
        return new TokenResponse(accessToken, refreshToken);
    }
}
