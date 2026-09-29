package com.mirattic.flow.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Mirattic Auth(auth.mirattic.com) 와의 통신. Flow 백엔드는 기밀 클라이언트 `mirattic-flow` 다.
 * client secret 은 이 서버에만 있고, 브라우저는 토큰을 HttpOnly 쿠키로만 들고 다닌다.
 *
 * 왜 Flow 가 직접 토큰을 발급하지 않고 Auth 토큰을 그대로 쓰나:
 * Auth 에서 계정을 막거나 비밀번호를 바꾸면 Flow 에도 최대 15분(access token 수명) 안에 반영된다.
 * Flow 가 자기 토큰을 따로 발급하면 그 토큰이 만료될 때(최대 30일)까지 반영되지 않는다.
 */
@Slf4j
@Component
public class MiratticAuth {

    /** Auth 토큰 엔드포인트의 응답. 필드 이름은 OAuth 규격의 이름 그대로다. */
    public record TokenResponse(String access_token, String refresh_token, String id_token) {}

    private final String issuer;
    private final String clientId;
    private final String redirectUri;
    private final String postLogoutRedirectUri;
    private final RestClient http;
    private final NimbusJwtDecoder idTokenDecoder;

    public MiratticAuth(@Value("${app.auth.issuer}") String issuer,
                        @Value("${app.auth.client-id}") String clientId,
                        @Value("${app.auth.client-secret}") String clientSecret,
                        @Value("${app.auth.redirect-uri}") String redirectUri,
                        @Value("${app.auth.post-logout-redirect-uri}") String postLogoutRedirectUri) {
        this.issuer = issuer;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder()
                .baseUrl(issuer)
                .requestFactory(timeouts)
                // RFC 6749 2.3.1: Basic 인증 전에 id 와 secret 을 폼 인코딩한다 (base64 secret 의 + / = 때문).
                .defaultHeaders(h -> h.setBasicAuth(form(clientId), form(clientSecret)))
                .build();
        this.idTokenDecoder = decoder(null);
    }

    public String issuer() { return issuer; }
    public String clientId() { return clientId; }
    public String redirectUri() { return redirectUri; }
    public String postLogoutRedirectUri() { return postLogoutRedirectUri; }

    /**
     * 서명(Auth JWKS) · iss · exp · aud = mirattic-flow 를 검사하는 디코더.
     * API 용은 extra 로 "access token 만"(scope 있음) 을 더한다 — ID token 도 같은 키 · iss · aud 라서.
     */
    public NimbusJwtDecoder decoder(OAuth2TokenValidator<Jwt> extra) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks").build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong audience", null));
        OAuth2TokenValidator<Jwt> base = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audience);
        decoder.setJwtValidator(extra == null ? base : new DelegatingOAuth2TokenValidator<>(base, extra));
        return decoder;
    }

    public JwtDecoder idTokenDecoder() {
        return idTokenDecoder;
    }

    /** 인가 코드 + 우리 PKCE verifier → Auth 토큰. 실패하면 예외 (호출한 쪽이 로그인 실패로 처리). */
    public TokenResponse exchange(String code, String verifier) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("code_verifier", verifier);
        return post("/oauth2/token", body, TokenResponse.class);
    }

    /** refresh token 회전. Auth 가 거절하면(400 invalid_grant) HttpClientErrorException.BadRequest. */
    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        return post("/oauth2/token", body, TokenResponse.class);
    }

    /** Auth 가 refresh token 을 거절한 이유가 invalid_grant(만료 · 폐기 · 회전됨)인가. */
    public static boolean isInvalidGrant(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            return body != null && "invalid_grant".equals(body.get("error"));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * 로그아웃: 이 로그인의 토큰을 Auth 에서 폐기한다. 실패하면 RestClientException — 호출한 쪽은 쿠키를 지우지 말고
     * 다시 시도하게 해야 한다 (쿠키를 지우면 폐기되지 않은 토큰을 다시 폐기할 방법이 없다).
     */
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", refreshToken);
        body.add("token_type_hint", "refresh_token");
        post("/oauth2/revoke", body, Void.class);
    }

    /** 탈퇴 확인처럼 쓰고 버리는 로그인: 폐기에 실패해도 흐름을 막지 않는다 (그 토큰은 어디에도 저장되지 않았다). */
    public void revokeQuietly(String refreshToken) {
        try {
            revoke(refreshToken);
        } catch (RestClientException e) {
            log.warn("Mirattic Auth revoke 실패: {}", e.getClass().getSimpleName());
        }
    }

    private <T> T post(String path, MultiValueMap<String, String> body, Class<T> type) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(type);
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
