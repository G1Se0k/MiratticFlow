package com.mirattic.flow.auth.controller;

import com.mirattic.flow.auth.service.AuthCookies;
import com.mirattic.flow.global.security.CsrfHeaderFilter;
import com.mirattic.flow.support.ApiTestSupport;
import com.mirattic.flow.support.AuthStub;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mirattic 계정 로그인 (Auth 는 AuthStub) · 쿠키 · 재발급 · 로그아웃 · CSRF 헤더. */
class AuthApiTest extends ApiTestSupport {

    private static String uid() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("처음 로그인하면 Flow 사용자가 생기고 HttpOnly 쿠키로 인증된다")
    void signInCreatesUserAndSetsCookies() throws Exception {
        MockHttpServletResponse login = signIn(uid(), "first@test.com", "처음온사람");
        assertThat(login.getRedirectedUrl()).isEqualTo("/");
        Cookie access = login.getCookie(AuthCookies.ACCESS);
        Cookie refresh = login.getCookie(AuthCookies.REFRESH);
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/auth");

        mockMvc.perform(get("/api/users/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("first@test.com"))
                .andExpect(jsonPath("$.name").value("처음온사람"));
    }

    @Test
    @DisplayName("같은 Mirattic 계정으로 다시 로그인하면 같은 Flow 사용자다")
    void sameAccountSameUser() throws Exception {
        String uid = uid();
        Cookie first = signIn(uid, "a@test.com", "A").getCookie(AuthCookies.ACCESS);
        Cookie second = signIn(uid, "a@test.com", "A").getCookie(AuthCookies.ACCESS);
        assertThat(id(bodyOf(mockMvc.perform(get("/api/users/me").cookie(second)))))
                .isEqualTo(id(bodyOf(mockMvc.perform(get("/api/users/me").cookie(first)))));
    }

    @Test
    @DisplayName("로그인 뒤 원래 화면으로 돌아가되, 다른 사이트로는 보내지 않는다")
    void nextStaysOnSite() throws Exception {
        assertThat(signIn(uid(), null, "N", "/invite/abc").getRedirectedUrl()).isEqualTo("/invite/abc");
        assertThat(signIn(uid(), null, "N", "//evil.example").getRedirectedUrl()).isEqualTo("/");
        assertThat(signIn(uid(), null, "N", "https://evil.example").getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    @DisplayName("로그인을 시작한 브라우저(state 쿠키)가 아니면 콜백이 거절된다 · state 는 한 번만 쓴다")
    void callbackNeedsTheStateCookie() throws Exception {
        MockHttpServletResponse start = mockMvc.perform(get("/auth/start")).andReturn().getResponse();
        String state = java.net.URLDecoder.decode(org.springframework.web.util.UriComponentsBuilder
                .fromUriString(start.getRedirectedUrl()).build().getQueryParams().getFirst("state"),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(mockMvc.perform(get("/auth/callback").param("code", "c").param("state", state))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo("/login?error=failed");
        assertThat(mockMvc.perform(get("/auth/callback").param("code", "c").param("state", state)
                        .cookie(start.getCookie("flow_login")))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo("/login?error=expired");
    }

    @Test
    @DisplayName("재발급은 refresh 쿠키로 하고 회전된다 — 이전 토큰은 401 과 함께 쿠키가 지워진다")
    void refreshRotates() throws Exception {
        Cookie refresh = signIn(uid(), null, "R").getCookie(AuthCookies.REFRESH);

        MockHttpServletResponse renewed = mockMvc.perform(post("/api/auth/refresh").cookie(refresh)
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(renewed.getCookie(AuthCookies.REFRESH).getValue()).isNotEqualTo(refresh.getValue());
        mockMvc.perform(get("/api/users/me").cookie(renewed.getCookie(AuthCookies.ACCESS))).andExpect(status().isOk());

        MockHttpServletResponse stale = mockMvc.perform(post("/api/auth/refresh").cookie(refresh)
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        // 다른 탭이 방금 회전해 받은 새 쿠키를 늦게 온 이 응답이 지우면 안 된다.
        assertThat(stale.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("Auth 의 invalid_grant 가 아닌 400 은 로그아웃이 아니라 502 다")
    void otherAuthErrorsKeepTheSession() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH, AuthStub.INVALID_REQUEST))
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("Bearer 가 아닌 Authorization 헤더로는 CSRF 검사를 건너뛸 수 없다 · 재발급은 Bearer 가 있어도 검사한다")
    void onlyARealBearerSkipsTheCsrfCheck() throws Exception {
        MockHttpServletResponse login = signIn(uid(), null, "B");
        String body = json(Map.of("name", "x"));
        mockMvc.perform(patch("/api/users/me").cookie(login.getCookie(AuthCookies.ACCESS))
                        .header("Authorization", "Basic Zm9vOmJhcg==")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh").cookie(login.getCookie(AuthCookies.REFRESH))
                        .header("Authorization", "Bearer anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그아웃하면 Auth 에서 토큰이 폐기되고 쿠키가 지워지며, Auth 로그인 세션을 끝낼 폼 정보가 온다")
    void logout() throws Exception {
        MockHttpServletResponse login = signIn(uid(), null, "L");
        Cookie refresh = login.getCookie(AuthCookies.REFRESH);
        Cookie id = login.getCookie(AuthCookies.ID);
        assertThat(id.isHttpOnly()).isTrue();
        assertThat(id.getPath()).isEqualTo("/api/auth");

        MockHttpServletResponse out = mockMvc.perform(post("/api/auth/logout").cookie(refresh, id)
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value(AuthStub.issuer() + "/connect/logout"))
                .andExpect(jsonPath("$.idTokenHint").value(id.getValue()))
                .andExpect(jsonPath("$.postLogoutRedirectUri").value("http://127.0.0.1:3000/login?logout=success"))
                .andReturn().getResponse();
        assertThat(AuthStub.REVOKED).contains(refresh.getValue());
        assertThat(out.getCookie(AuthCookies.REFRESH).getMaxAge()).isZero();
        assertThat(out.getCookie(AuthCookies.ID).getMaxAge()).isZero();
    }

    @Test
    @DisplayName("로그아웃한 브라우저의 다음 로그인은 prompt=login 으로 비밀번호를 다시 묻고, 로그인하면 표시가 지워진다")
    void loginAfterLogoutForcesReauthentication() throws Exception {
        MockHttpServletResponse login = signIn(uid(), null, "R");
        MockHttpServletResponse out = mockMvc.perform(post("/api/auth/logout")
                        .cookie(login.getCookie(AuthCookies.REFRESH), login.getCookie(AuthCookies.ID))
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie marker = out.getCookie(AuthCookies.REAUTH);
        assertThat(marker.isHttpOnly()).isTrue();

        MockHttpServletResponse start = mockMvc.perform(get("/auth/start").cookie(marker)).andReturn().getResponse();
        assertThat(start.getRedirectedUrl()).contains("prompt=login");
        assertThat(mockMvc.perform(get("/auth/start")).andReturn().getResponse().getRedirectedUrl())
                .doesNotContain("prompt=");

        // The re-authenticated sign-in completes and clears the marker.
        var query = org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getRedirectedUrl()).build()
                .getQueryParams();
        String code = AuthStub.code(uid(), null, "R2", decode(query.getFirst("code_challenge")),
                decode(query.getFirst("redirect_uri")));
        MockHttpServletResponse cb = mockMvc.perform(get("/auth/callback").param("code", code)
                        .param("state", decode(query.getFirst("state"))).cookie(start.getCookie("flow_login"), marker))
                .andReturn().getResponse();
        assertThat(cb.getRedirectedUrl()).isEqualTo("/");
        assertThat(cb.getCookie(AuthCookies.REAUTH).getMaxAge()).isZero();
    }

    @Test
    @DisplayName("로그아웃 전에 (다른 탭에서) 시작한 로그인은 로그아웃 뒤에 끝나도 받지 않는다")
    void loginStartedBeforeLogoutIsRejected() throws Exception {
        MockHttpServletResponse start = mockMvc.perform(get("/auth/start")).andReturn().getResponse();
        MockHttpServletResponse login = signIn(uid(), null, "T");
        MockHttpServletResponse out = mockMvc.perform(post("/api/auth/logout").cookie(login.getCookie(AuthCookies.REFRESH))
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isOk()).andReturn().getResponse();
        // Logout drops the in-progress login's state cookie...
        assertThat(out.getCookie("flow_login").getMaxAge()).isZero();

        // ...and even if that callback still carried it, the login did not re-ask for the password: refused.
        var query = org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getRedirectedUrl()).build()
                .getQueryParams();
        String code = AuthStub.code(uid(), null, "late", decode(query.getFirst("code_challenge")),
                decode(query.getFirst("redirect_uri")));
        MockHttpServletResponse late = mockMvc.perform(get("/auth/callback").param("code", code)
                        .param("state", decode(query.getFirst("state")))
                        .cookie(start.getCookie("flow_login"), out.getCookie(AuthCookies.REAUTH)))
                .andReturn().getResponse();
        assertThat(late.getRedirectedUrl()).isEqualTo("/login?error=expired");
        assertThat(late.getCookie(AuthCookies.ACCESS)).isNull();
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Auth 에서 폐기하지 못하면 502 이고 쿠키를 지우지 않는다 (다시 시도할 수 있게)")
    void logoutKeepsCookiesWhenRevokeFails() throws Exception {
        MockHttpServletResponse out = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(AuthCookies.REFRESH, AuthStub.REVOKE_FAILS))
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isBadGateway()).andReturn().getResponse();
        assertThat(out.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("쿠키로 인증한 쓰기 요청은 X-Requested-With 헤더가 있어야 한다 (CSRF)")
    void cookieWritesNeedTheHeader() throws Exception {
        Cookie access = signIn(uid(), null, "C").getCookie(AuthCookies.ACCESS);
        String body = json(Map.of("name", "새이름"));

        mockMvc.perform(patch("/api/users/me").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/users/me").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(CsrfHeaderFilter.HEADER, CsrfHeaderFilter.VALUE))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Flow 의 Auth access token 만 통한다 — 다른 클라이언트 토큰, ID token, 모르는 계정, 토큰 없음은 401")
    void onlyFlowAccessTokens() throws Exception {
        String uid = uid();
        signIn(uid, null, "T");
        authed(get("/api/users/me"), AuthStub.accessToken(uid)).andExpect(status().isOk());

        authed(get("/api/users/me"), AuthStub.sign(Map.of("sub", uid, "aud", "mirattic-sync", "scope", "openid")))
                .andExpect(status().isUnauthorized());
        authed(get("/api/users/me"), AuthStub.sign(Map.of("sub", uid, "aud", AuthStub.CLIENT_ID)))
                .andExpect(status().isUnauthorized());
        authed(get("/api/users/me"), AuthStub.accessToken(uid())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
