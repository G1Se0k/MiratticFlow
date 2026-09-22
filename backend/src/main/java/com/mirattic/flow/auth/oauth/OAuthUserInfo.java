package com.mirattic.flow.auth.oauth;

import com.mirattic.flow.user.entity.AuthProvider;

/**
 * 각 소셜 서비스의 응답 형태가 제각각이므로, 우리가 필요한 값만 뽑아 이 형태로 통일한다.
 * email 은 제공받지 못할 수 있어 null 이 가능하다.
 */
public record OAuthUserInfo(AuthProvider provider, String providerId, String email, String name) {
}
