package com.mirattic.flow.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Auth 토큰을 담는 HttpOnly 쿠키. localStorage 와 달리 스크립트가 읽을 수 없어 XSS 로 토큰이 새지 않는다.
 *
 * - flow_at: access token (15분). 모든 경로에 보낸다 (API · WebSocket 핸드셰이크).
 * - flow_rt: refresh token (30일). /api/auth 로만 보낸다 — 재발급 · 로그아웃 말고는 쓸 일이 없다.
 * - flow_id: ID token (30일, /api/auth). 로그아웃 때 Auth 의 로그인 세션(SSO)까지 끝내는 데 필요하다 (id_token_hint).
 * SameSite=Lax: 다른 사이트에서 오는 POST 에는 붙지 않는다 (CSRF 1차 방어. 2차는 CsrfHeaderFilter).
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "flow_at";
    public static final String REFRESH = "flow_rt";
    public static final String ID = "flow_id";
    /**
     * 로그아웃 · 탈퇴 표시 (30일, /auth). 있으면 다음 로그인에 prompt=login 을 붙여 Auth 가 비밀번호를 다시 묻는다.
     * Auth 의 로그인 세션을 끝내는 단계(브라우저가 Auth 로 가는 폼)가 실패해도 그 세션이 조용히 재사용되지 않게 한다.
     */
    public static final String REAUTH = "flow_reauth";

    private final boolean secure;

    public AuthCookies(@Value("${app.auth.redirect-uri}") String redirectUri) {
        // 로컬(http://127.0.0.1:3000)에서는 Secure 쿠키가 저장되지 않는다.
        this.secure = redirectUri.startsWith("https://");
    }

    public void set(HttpServletResponse response, MiratticAuth.TokenResponse tokens) {
        add(response, ACCESS, tokens.access_token(), "/", Duration.ofMinutes(15));
        add(response, REFRESH, tokens.refresh_token(), "/api/auth", Duration.ofDays(30));
        // 재발급 때도 Auth 가 새 ID token 을 준다 (그 로그인에 저장된 것이 바뀌므로 함께 바꿔야 로그아웃 힌트가 맞는다).
        if (tokens.id_token() != null) {
            add(response, ID, tokens.id_token(), "/api/auth", Duration.ofDays(30));
        }
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS, "", "/", Duration.ZERO);
        add(response, REFRESH, "", "/api/auth", Duration.ZERO);
        add(response, ID, "", "/api/auth", Duration.ZERO);
    }

    public void requireReauth(HttpServletResponse response) {
        add(response, REAUTH, "1", "/auth", Duration.ofDays(30));
        // 진행 중이던 로그인(다른 탭에서 시작한 것)의 state 쿠키도 지운다 — 그 콜백은 이제 state 확인에서 거절된다.
        add(response, "flow_login", "", "/auth/callback", Duration.ZERO);
    }

    public void reauthDone(HttpServletResponse response) {
        add(response, REAUTH, "", "/auth", Duration.ZERO);
    }

    public static String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .httpOnly(true).secure(secure).sameSite("Lax").path(path).maxAge(maxAge).build().toString());
    }

    public boolean secure() {
        return secure;
    }
}
