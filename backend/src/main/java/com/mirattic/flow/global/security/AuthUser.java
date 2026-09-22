package com.mirattic.flow.global.security;

/**
 * 인증된 사용자. 토큰 안에 들어 있는 정보만 담으므로 요청마다 DB 를 조회하지 않는다.
 * 컨트롤러에서 @AuthenticationPrincipal AuthUser 로 꺼내 쓴다.
 */
public record AuthUser(Long id, String email) {}
