package com.mirattic.flow.support;

import com.mirattic.flow.auth.service.AuthCookies;
import com.mirattic.flow.project.dto.AddMemberRequest;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.workspace.dto.WorkspaceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 통합 테스트의 공통 준비 코드.
 *
 * 테스트마다 "가입 → 로그인 → 워크스페이스 → 프로젝트 → 참여자"를 다시 만드는 코드가
 * 파일마다 복사돼 있었다. 그러면 각 테스트에서 정작 **무엇을 검증하는지**가 묻힌다.
 * 준비를 여기로 모아 두면 테스트 본문에는 확인하려는 내용만 남는다.
 *
 * Spring 의 테스트 애너테이션은 부모 클래스에서도 찾으므로 상속만 하면 된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    @DynamicPropertySource
    static void auth(DynamicPropertyRegistry registry) {
        registry.add("app.auth.issuer", AuthStub::issuer);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 기본

    protected String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    /** 응답 본문에서 id 를 꺼낸다. 만든 것의 id 로 다음 요청을 이어가는 일이 많다. */
    protected long id(String body) {
        return objectMapper.readTree(body).get("id").asLong();
    }

    protected String field(String body, String name) {
        return objectMapper.readTree(body).get(name).asString();
    }

    protected ResultActions authed(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return mockMvc.perform(builder.header("Authorization", "Bearer " + token));
    }

    protected String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    // ---------------------------------------------------------------- 사용자

    protected String newUserToken() throws Exception {
        return newUserToken("테스터");
    }

    /**
     * Mirattic 계정으로 로그인한 새 사용자의 access token (Bearer 헤더로 쓴다).
     * 실제 로그인 흐름 그대로: /auth/start → (Auth 로그인: AuthStub) → /auth/callback → flow_at 쿠키.
     */
    protected String newUserToken(String name) throws Exception {
        String uid = UUID.randomUUID().toString();
        return signIn(uid, "u" + uid.substring(0, 8) + "@test.com", name).getCookie(AuthCookies.ACCESS).getValue();
    }

    /** 로그인 흐름을 끝까지 돌리고 콜백의 응답(쿠키 · 리다이렉트)을 돌려준다. */
    protected MockHttpServletResponse signIn(String uid, String email, String name) throws Exception {
        return signIn(uid, email, name, null);
    }

    protected MockHttpServletResponse signIn(String uid, String email, String name, String next) throws Exception {
        return signIn(uid, email, name, next, Instant.now());
    }

    /** authTime: 그 Auth 로그인의 시각 (ID token auth_time) — 예: 삭제 전에 받아 둔 로그인이 뒤늦게 닿는 경우. */
    protected MockHttpServletResponse signIn(String uid, String email, String name, String next, Instant authTime)
            throws Exception {
        String verifier = UUID.randomUUID() + "-verifier-verifier";
        MockHttpServletRequestBuilder startRequest = get("/auth/start");
        if (next != null) {
            startRequest.param("next", next);
        }
        MockHttpServletResponse start = mockMvc.perform(startRequest).andReturn().getResponse();
        String authorize = start.getRedirectedUrl();
        var query = UriComponentsBuilder.fromUriString(authorize).build().getQueryParams();
        // PKCE: 테스트가 Auth 역할을 하므로 Flow 가 만든 challenge 에 code 를 묶는다.
        String code = AuthStub.code(uid, email, name, decode(query.getFirst("code_challenge")),
                decode(query.getFirst("redirect_uri")), authTime);
        return mockMvc.perform(get("/auth/callback").param("code", code).param("state", decode(query.getFirst("state")))
                .cookie(start.getCookie("flow_login"))).andReturn().getResponse();
    }

    /**
     * 회원 탈퇴 (Auth 재확인 흐름): /auth/start?withdraw=true (지금 로그인한 쿠키로) → Auth 에서 다시 로그인
     * (who · authTime) → /auth/callback. 콜백의 응답(탈퇴 성공이면 Auth 로그아웃 폼 페이지, 아니면 /account 로 리다이렉트).
     */
    protected MockHttpServletResponse withdraw(String accessToken, String who, Instant authTime) throws Exception {
        MockHttpServletRequestBuilder startRequest = get("/auth/start").param("withdraw", "true");
        if (accessToken != null) {
            startRequest.cookie(new jakarta.servlet.http.Cookie(AuthCookies.ACCESS, accessToken));
        }
        MockHttpServletResponse start = mockMvc.perform(startRequest).andReturn().getResponse();
        if (!start.getRedirectedUrl().startsWith(AuthStub.issuer())) {
            return start; // 시작 단계에서 막혔다 (/account?withdraw=...)
        }
        var query = UriComponentsBuilder.fromUriString(start.getRedirectedUrl()).build().getQueryParams();
        assertThat(query.getFirst("prompt")).isEqualTo("login");
        String code = AuthStub.code(who, null, "탈퇴", decode(query.getFirst("code_challenge")),
                decode(query.getFirst("redirect_uri")), authTime);
        return mockMvc.perform(get("/auth/callback").param("code", code).param("state", decode(query.getFirst("state")))
                .cookie(start.getCookie("flow_login"))).andReturn().getResponse();
    }

    private static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    protected long userIdOf(String token) throws Exception {
        return id(bodyOf(authed(get("/api/users/me"), token)));
    }

    // ---------------------------------------------------------------- 워크스페이스 · 프로젝트

    protected long createWorkspace(String token) throws Exception {
        return createWorkspace(token, "우리팀");
    }

    protected long createWorkspace(String token, String name) throws Exception {
        return id(bodyOf(authed(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WorkspaceRequest(name, "설명"))), token)
                .andExpect(status().isCreated())));
    }

    protected long createProject(String token, long workspaceId, String name) throws Exception {
        return id(bodyOf(authed(post("/api/workspaces/{id}/projects", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProjectRequest(name, null, null))), token)
                .andExpect(status().isCreated())));
    }

    /** 워크스페이스와 프로젝트를 한 번에 만들고 프로젝트 id 를 돌려준다. */
    protected long setUpProject(String token) throws Exception {
        return setUpProject(token, "프로젝트");
    }

    protected long setUpProject(String token, String projectName) throws Exception {
        return createProject(token, createWorkspace(token, "팀"), projectName);
    }

    protected long workspaceIdOfProject(long projectId, String token) throws Exception {
        return objectMapper.readTree(bodyOf(authed(get("/api/projects/{id}", projectId), token)))
                .get("workspaceId").asLong();
    }

    // ---------------------------------------------------------------- 참여자

    protected String inviteCode(String ownerToken, long workspaceId) throws Exception {
        return field(bodyOf(authed(get("/api/workspaces/{id}/invite-code", workspaceId), ownerToken)), "code");
    }

    protected String addProjectMember(String ownerToken, long projectId) throws Exception {
        return addProjectMember(ownerToken, projectId, "테스터");
    }

    /**
     * 새 사용자를 만들어 워크스페이스에 들여보내고 프로젝트 참여자로 넣는다.
     * 권한 테스트마다 필요한 "제3자"를 만드는 가장 짧은 길이다.
     */
    protected String addProjectMember(String ownerToken, long projectId, String name) throws Exception {
        String code = inviteCode(ownerToken, workspaceIdOfProject(projectId, ownerToken));

        String token = newUserToken(name);
        authed(post("/api/invites/{code}/accept", code), token).andExpect(status().isOk());
        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(userIdOf(token)))), ownerToken)
                .andExpect(status().isCreated());
        return token;
    }
}
