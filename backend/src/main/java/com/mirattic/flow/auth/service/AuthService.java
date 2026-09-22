package com.mirattic.flow.auth.service;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.auth.dto.TokenResponse;
import com.mirattic.flow.auth.entity.RefreshToken;
import com.mirattic.flow.auth.repository.RefreshTokenRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.auth.oauth.OAuthClient;
import com.mirattic.flow.auth.oauth.OAuthUserInfo;
import com.mirattic.flow.global.security.JwtProvider;
import com.mirattic.flow.user.entity.AuthProvider;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    /** 스프링이 구현체를 모두 주입해주므로, 소셜 서비스를 추가해도 이 클래스는 바뀌지 않는다. */
    private final Map<AuthProvider, OAuthClient> oauthClients;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       List<OAuthClient> oauthClients) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.oauthClients = oauthClients.stream()
                .collect(Collectors.toMap(OAuthClient::provider, client -> client));
    }

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
        // 소셜 가입자는 비밀번호가 없다. 여기서 막지 않으면 matches() 가 null 을 받는다.
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return issueTokens(user);
    }

    /**
     * 소셜 로그인. 인가 코드를 소셜 서비스에 넘겨 사용자 정보를 받아오고,
     * 처음 온 사용자면 가입까지 함께 처리한 뒤 우리 JWT 를 발급한다.
     */
    @Transactional
    public TokenResponse loginWithOAuth(AuthProvider provider, String code, String state) {
        OAuthClient client = oauthClients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        OAuthUserInfo userInfo = client.fetch(code, state);

        User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
                .orElseGet(() -> registerSocialUser(userInfo));
        return issueTokens(user);
    }

    private User registerSocialUser(OAuthUserInfo userInfo) {
        // 이메일이 같다는 이유로 기존 계정에 자동 연결하지 않는다.
        // 이메일을 검증하지 않는 소셜 서비스가 섞이면 남의 계정을 가져갈 수 있다.
        // 계정 연결은 로그인한 상태에서 명시적으로 하는 것이 맞다.
        if (userInfo.email() != null && userRepository.existsByEmail(userInfo.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return userRepository.save(User.createSocial(
                userInfo.provider(), userInfo.providerId(), userInfo.email(), userInfo.name()));
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
