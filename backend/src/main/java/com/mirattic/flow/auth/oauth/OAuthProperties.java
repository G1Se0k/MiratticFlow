package com.mirattic.flow.auth.oauth;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * app.oauth.registration.<provider>.* 설정을 담는다.
 * client-secret 은 절대 저장소에 두지 않고 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(Map<String, Registration> registration) {

    public record Registration(String clientId, String clientSecret, String redirectUri) {
    }

    /**
     * 애플리케이션 기동 시점이 아니라 실제 호출 시점에 확인한다.
     * 키를 넣지 않았다는 이유로 서버 전체가 뜨지 않으면 안 되기 때문이다.
     */
    public Registration get(String provider) {
        Registration found = registration == null ? null : registration.get(provider.toLowerCase());
        if (found == null || found.clientId() == null || found.clientId().isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_NOT_CONFIGURED);
        }
        return found;
    }
}
