package com.mirattic.flow.global.security;

/**
 * 인증된 사용자 (Flow 의 users.id). 요청의 Mirattic Auth 토큰(sub)으로 찾은 값이다.
 * 컨트롤러에서 @AuthenticationPrincipal AuthUser 로 꺼내 쓴다.
 */
public record AuthUser(Long id) {}
