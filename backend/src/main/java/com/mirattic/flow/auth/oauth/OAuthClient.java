package com.mirattic.flow.auth.oauth;

import com.mirattic.flow.user.entity.AuthProvider;

/** 소셜 서비스 하나당 구현 하나. AuthService 는 어느 서비스인지 몰라도 된다. */
public interface OAuthClient {

    AuthProvider provider();

    /**
     * 인가 코드를 받아 소셜 서비스의 access token 으로 교환하고, 사용자 정보를 조회한다.
     *
     * @param state 네이버는 토큰 교환 시에도 state 를 요구한다. 카카오는 쓰지 않는다.
     */
    OAuthUserInfo fetch(String code, String state);
}
