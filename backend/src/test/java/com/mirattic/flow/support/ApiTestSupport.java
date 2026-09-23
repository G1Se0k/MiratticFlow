package com.mirattic.flow.support;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.project.dto.AddMemberRequest;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.workspace.dto.WorkspaceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

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

    /** 가입과 로그인을 한 번에. 이메일은 매번 달라야 하므로 UUID 를 섞는다. */
    protected String newUserToken(String name) throws Exception {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest(email, "password123", name))));
        return field(bodyOf(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(email, "password123"))))), "accessToken");
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
