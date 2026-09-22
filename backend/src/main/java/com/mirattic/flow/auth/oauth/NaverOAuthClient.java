package com.mirattic.flow.auth.oauth;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.entity.AuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** https://developers.naver.com/docs/login/api */
@Slf4j
@Component
public class NaverOAuthClient implements OAuthClient {

    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final OAuthProperties properties;

    public NaverOAuthClient(OAuthProperties properties) {
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    private OAuthProperties.Registration config() {
        return properties.get("naver");
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
    }

    @Override
    public OAuthUserInfo fetch(String code, String state) {
        String accessToken = requestAccessToken(code, state);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String state) {
        // 네이버는 토큰 교환을 GET 쿼리스트링으로 받고, state 도 함께 요구한다.
        String url = UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", config().clientId())
                .queryParam("client_secret", config().clientSecret())
                .queryParam("code", code)
                .queryParam("state", state)
                .build(true)
                .toUriString();

        try {
            JsonNode response = restClient.get().uri(url).retrieve().body(JsonNode.class);
            if (response == null || response.path("access_token").isMissingNode()) {
                log.warn("네이버 토큰 교환 응답에 access_token 없음: {}", response);
                throw new BusinessException(ErrorCode.OAUTH_FAILED);
            }
            return response.path("access_token").asString();
        } catch (RestClientException e) {
            log.warn("네이버 토큰 교환 실패", e);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }

    private OAuthUserInfo requestUserInfo(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(USER_INFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("네이버 사용자 정보 조회 실패", e);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        // 네이버는 실제 사용자 정보를 response 필드 안에 한 번 더 감싸서 준다.
        JsonNode profile = body == null ? null : body.path("response");
        if (profile == null || profile.path("id").isMissingNode()) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        String email = profile.path("email").isMissingNode() ? null : profile.path("email").asString();
        String name = profile.path("name").isMissingNode()
                ? (profile.path("nickname").isMissingNode() ? "네이버사용자" : profile.path("nickname").asString())
                : profile.path("name").asString();

        return new OAuthUserInfo(AuthProvider.NAVER, profile.path("id").asString(), email, name);
    }
}
