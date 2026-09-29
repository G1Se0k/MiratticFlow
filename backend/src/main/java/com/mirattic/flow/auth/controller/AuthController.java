package com.mirattic.flow.auth.controller;

import com.mirattic.flow.auth.service.AuthCookies;
import com.mirattic.flow.auth.service.MiratticAuth;
import com.mirattic.flow.global.config.SocketExpiry;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * 로그인 유지. refresh token 은 flow_rt 쿠키에만 있고, 여기서 Auth 로 전달해 새 쿠키로 바꾼다.
 * Auth 가 refresh token 을 매번 회전하고, 폐기된 토큰이 다시 오면 그 로그인 전체를 끊는다.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MiratticAuth auth;
    private final AuthCookies cookies;
    private final JwtDecoder jwtDecoder;
    private final UserService userService;
    private final SocketExpiry socketExpiry;

    /** 204 = 새 쿠키. 401 = 로그인이 끝났다. 502 = Auth 에 닿지 못했거나 설정 문제 (로그인은 유지). */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = AuthCookies.read(request, AuthCookies.REFRESH);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            cookies.set(response, auth.refresh(refreshToken));
        } catch (HttpClientErrorException.BadRequest e) {
            if (!MiratticAuth.isInvalidGrant(e)) {
                // invalid_request 같은 다른 400 은 설정 · 요청 문제다. 멀쩡한 로그인을 끊지 않는다.
                log.warn("Mirattic Auth refresh 거절: {}", e.getResponseBodyAsString());
                throw new BusinessException(ErrorCode.AUTH_UNAVAILABLE);
            }
            // invalid_grant: 만료 · 폐기 · 이미 회전된 토큰. 이것만 "로그인이 끝났다"이다.
            // 쿠키는 지우지 않는다 — 다른 탭이 방금 회전해 받은 새 쿠키를 늦게 도착한 이 응답이 지워 버릴 수 있다.
            // 남은 옛 refresh 쿠키는 /api/auth 로만 가고 만료되면 사라진다. 로그아웃은 쿠키를 지운다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        } catch (RestClientException e) {
            // 그 외(예: client secret 설정 오류, Auth 장애)로 모든 사용자를 로그아웃시키지 않는다.
            log.warn("Mirattic Auth refresh 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUTH_UNAVAILABLE);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Auth 의 로그인 세션(SSO)을 끝내러 브라우저가 보낼 폼. OIDC RP-Initiated Logout 을 POST 로 한다 —
     * GET 이면 ID token 이 주소창 · 방문 기록에 남는다. ID token 쿠키가 없으면(아주 오래된 로그인) null.
     */
    public record LogoutResponse(String endpoint, String idTokenHint, String postLogoutRedirectUri) {}

    /**
     * 로그아웃. Auth 에서 refresh token 을 폐기하고 쿠키를 지운 뒤, Auth 세션을 끝낼 폼 정보를 돌려준다.
     * Flow 쿠키만 지우면 Auth 의 로그인 세션이 남아 공용 PC 의 다음 사람이 "로그인" 버튼 하나로 이 계정에 들어온다.
     * 폐기에 실패하면 502 이고 쿠키를 지우지 않는다 — 다시 누르면 다시 폐기를 시도한다.
     */
    @PostMapping("/logout")
    public LogoutResponse logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = AuthCookies.read(request, AuthCookies.REFRESH);
        String idToken = AuthCookies.read(request, AuthCookies.ID);
        if (refreshToken != null) {
            try {
                auth.revoke(refreshToken);
            } catch (RestClientException e) {
                log.warn("Mirattic Auth revoke 실패: {}", e.getClass().getSimpleName());
                throw new BusinessException(ErrorCode.AUTH_UNAVAILABLE);
            }
        }
        closeSockets(request);
        cookies.clear(response);
        cookies.requireReauth(response);
        return idToken == null ? new LogoutResponse(null, null, null)
                : new LogoutResponse(auth.issuer() + "/connect/logout", idToken, auth.postLogoutRedirectUri());
    }

    /**
     * 다른 탭의 채팅 연결도 끝낸다. 연결은 인증에 쓴 토큰의 만료 시각에 닫히므로(SocketExpiry), 쿠키의 access token
     * 이 이미 만료됐다면 그보다 오래된 토큰으로 연 연결도 모두 닫혀 있다. 살아 있으면 그 사용자의 연결을 닫는다.
     */
    private void closeSockets(HttpServletRequest request) {
        String accessToken = AuthCookies.read(request, AuthCookies.ACCESS);
        if (accessToken == null) {
            return;
        }
        try {
            userService.resolve(jwtDecoder.decode(accessToken)).ifPresent(socketExpiry::closeUser);
        } catch (JwtException expiredOrInvalid) {
            // 만료된 토큰이면 그 토큰으로 연 연결은 이미 닫혔다.
        }
    }
}
