package com.mirattic.flow.auth.oauth;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.entity.AuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final OAuthProperties properties;

    public KakaoOAuthClient(OAuthProperties properties) {
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    private OAuthProperties.Registration config() {
        return properties.get("kakao");
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetch(String code, String state) {
        String accessToken = requestAccessToken(code);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config().clientId());
        form.add("redirect_uri", config().redirectUri());
        form.add("code", code);
        // 카카오는 client_secret 이 선택이다 (콘솔에서 켠 경우에만 필요).
        if (config().clientSecret() != null && !config().clientSecret().isBlank()) {
            form.add("client_secret", config().clientSecret());
        }

        JsonNode response = post(TOKEN_URL, form);
        return response.path("access_token").asString();
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
            log.warn("카카오 사용자 정보 조회 실패", e);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        if (body == null || body.path("id").isMissingNode()) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        JsonNode account = body.path("kakao_account");
        // 이메일은 선택 동의 항목이라 동의하지 않으면 내려오지 않는다.
        String email = account.path("email").isMissingNode() ? null : account.path("email").asString();
        String nickname = account.path("profile").path("nickname").isMissingNode()
                ? "카카오사용자"
                : account.path("profile").path("nickname").asString();

        return new OAuthUserInfo(AuthProvider.KAKAO, body.path("id").asString(), email, nickname);
    }

    private JsonNode post(String url, MultiValueMap<String, String> form) {
        try {
            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.path("access_token").isMissingNode()) {
                throw new BusinessException(ErrorCode.OAUTH_FAILED);
            }
            return response;
        } catch (RestClientException e) {
            // 인가 코드는 일회용이라 재사용하거나 만료되면 여기서 실패한다.
            log.warn("카카오 토큰 교환 실패", e);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }
}
