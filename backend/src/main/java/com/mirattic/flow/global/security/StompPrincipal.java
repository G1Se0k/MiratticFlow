package com.mirattic.flow.global.security;

import java.security.Principal;

/**
 * STOMP 세션에 붙는 사용자. CONNECT 프레임에서 토큰을 검증한 뒤 세션에 심어두고,
 * 이후 SUBSCRIBE·SEND 프레임에서 "누가 보냈는지"를 여기서 꺼낸다.
 */
public record StompPrincipal(Long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
