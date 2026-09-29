package com.mirattic.flow.global.security;

import com.mirattic.flow.auth.service.AuthCookies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 쿠키 인증의 CSRF 2차 방어. 상태를 바꾸는 /api 요청은 `X-Requested-With: flow` 헤더가 있어야 한다.
 *
 * 쿠키는 브라우저가 알아서 붙이므로, 다른 사이트의 폼이나 스크립트도 우리 API 를 "로그인한 채로" 부를 수 있다.
 * SameSite=Lax 가 다른 사이트의 POST 에는 쿠키를 빼지만, 같은 사이트(*.mirattic.com)에서는 붙는다.
 * 폼은 커스텀 헤더를 붙일 수 없고, 다른 출처의 fetch 가 커스텀 헤더를 붙이면 CORS 사전 요청에서 막힌다.
 * Bearer 헤더로 인증하는 요청(테스트 · 도구)과 인증 쿠키가 없는 요청은 해당 없다.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    private final AccessDeniedHandler denied;

    /** 거절은 다른 403 과 같은 JSON 으로 바로 쓴다 (sendError 는 인증 없는 /error 디스패치를 거쳐 401 이 된다). */
    public CsrfHeaderFilter(AccessDeniedHandler denied) {
        this.denied = denied;
    }

    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "flow";
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // 쿠키가 없는 요청은 쿠키로 인증될 수도 없다 — 그대로 보내면 인증 단계에서 401 이 된다.
        String path = request.getRequestURI();
        return SAFE.contains(request.getMethod()) || !path.startsWith("/api/")
                // Bearer 헤더가 실제 자격 증명일 때만 쿠키가 아니다. 다른 값(Basic 등)이면 쿠키가 쓰이므로 검사한다.
                // /api/auth 의 재발급 · 로그아웃은 헤더와 상관없이 항상 refresh 쿠키를 쓴다.
                || (bearer(request) && !path.startsWith("/api/auth/"))
                || (AuthCookies.read(request, AuthCookies.ACCESS) == null
                    && AuthCookies.read(request, AuthCookies.REFRESH) == null);
    }

    private static boolean bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!VALUE.equals(request.getHeader(HEADER))) {
            denied.handle(request, response, new AccessDeniedException("missing " + HEADER));
            return;
        }
        chain.doFilter(request, response);
    }
}
