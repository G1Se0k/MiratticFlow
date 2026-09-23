package com.mirattic.flow.issue;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.issue.dto.IssueStatusRequest;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
import com.mirattic.flow.project.dto.AddMemberRequest;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.workspace.dto.WorkspaceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 이슈 번호 채번, 담당자 제약, 검색·필터·정렬, 삭제 권한을 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IssueApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 헬퍼

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private String newUserToken() throws Exception {
        String email = "i" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest(email, "password123", "테스터"))));
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asString();
    }

    private ResultActions authed(MockHttpServletRequestBuilder builder, String token) throws Exception {
        return mockMvc.perform(builder.header("Authorization", "Bearer " + token));
    }

    private long userIdOf(String token) throws Exception {
        String body = authed(get("/api/users/me"), token).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** 워크스페이스 + 프로젝트를 만들고 프로젝트 id 를 돌려준다. */
    private long setUpProject(String token) throws Exception {
        String wsBody = authed(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WorkspaceRequest("팀", null))), token)
                .andReturn().getResponse().getContentAsString();
        long workspaceId = objectMapper.readTree(wsBody).get("id").asLong();

        String body = authed(post("/api/workspaces/{id}/projects", workspaceId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProjectRequest("프로젝트", null, null))), token)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long workspaceIdOfProject(long projectId, String token) throws Exception {
        String body = authed(get("/api/projects/{id}", projectId), token).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("workspaceId").asLong();
    }

    /** 새 사용자를 워크스페이스 → 프로젝트까지 참여시키고 토큰을 돌려준다. */
    private String addProjectMember(String ownerToken, long projectId) throws Exception {
        long workspaceId = workspaceIdOfProject(projectId, ownerToken);
        String codeBody = authed(get("/api/workspaces/{id}/invite-code", workspaceId), ownerToken)
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(codeBody).get("code").asString();

        String token = newUserToken();
        authed(post("/api/invites/{code}/accept", code), token).andExpect(status().isOk());
        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(userIdOf(token)))), ownerToken)
                .andExpect(status().isCreated());
        return token;
    }

    private long createIssue(String token, long projectId, IssueRequest request) throws Exception {
        String body = authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(request)), token)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // ---------------------------------------------------------------- 생성

    @Test
    @DisplayName("이슈 번호는 프로젝트마다 1번부터 매겨진다")
    void issueNumberIsPerProject() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long otherProjectId = setUpProject(token);

        authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("첫 이슈", null, null, null, null, null))), token)
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));

        authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("둘째 이슈", null, null, null, null, null))), token)
                .andExpect(jsonPath("$.number").value(2));

        // 다른 프로젝트는 다시 1번부터
        authed(post("/api/projects/{id}/issues", otherProjectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("다른 프로젝트 이슈", null, null, null, null, null))), token)
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 담당자로 지정할 수 없다")
    void assigneeMustBeProjectMember() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);
        long outsiderId = userIdOf(newUserToken());

        authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("담당자 지정", null, null, null, outsiderId, null))), ownerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 이슈 목록을 볼 수 없다")
    void outsiderCannotListIssues() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);

        authed(get("/api/projects/{id}/issues", projectId), newUserToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    // ---------------------------------------------------------------- 검색 / 필터 / 정렬

    @Test
    @DisplayName("상태·우선순위·담당자·검색어로 거를 수 있다")
    void searchAndFilter() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId);
        long memberId = userIdOf(memberToken);

        long urgent = createIssue(ownerToken, projectId,
                new IssueRequest("로그인 오류", "급함", null, IssuePriority.URGENT, memberId, null));
        createIssue(ownerToken, projectId, new IssueRequest("버튼 정렬", null, null, IssuePriority.LOW, null, null));
        authed(patch("/api/issues/{id}/status", urgent).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.IN_PROGRESS))), ownerToken)
                .andExpect(status().isOk());

        authed(get("/api/projects/{id}/issues", projectId), ownerToken)
                .andExpect(jsonPath("$.totalElements").value(2));

        authed(get("/api/projects/{id}/issues?status=IN_PROGRESS", projectId), ownerToken)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("로그인 오류"));

        authed(get("/api/projects/{id}/issues?priority=LOW", projectId), ownerToken)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("버튼 정렬"));

        authed(get("/api/projects/{id}/issues?assigneeId={uid}", projectId, memberId), ownerToken)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assigneeName").value("테스터"));

        authed(get("/api/projects/{id}/issues?keyword=로그인", projectId), ownerToken)
                .andExpect(jsonPath("$.totalElements").value(1));

        // 검색어를 지우면 빈 문자열이 온다 — 조건 없음으로 취급해야 한다
        authed(get("/api/projects/{id}/issues?keyword=", projectId), ownerToken)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("마감일 오름차순으로 정렬할 수 있다")
    void sortByDueDate() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        createIssue(token, projectId,
                new IssueRequest("나중", null, null, null, null, LocalDate.of(2026, 12, 31)));
        createIssue(token, projectId,
                new IssueRequest("먼저", null, null, null, null, LocalDate.of(2026, 1, 1)));

        authed(get("/api/projects/{id}/issues?sort=dueDate,asc", projectId), token)
                .andExpect(jsonPath("$.content[0].title").value("먼저"))
                .andExpect(jsonPath("$.content[1].title").value("나중"));
    }

    // ---------------------------------------------------------------- 수정 / 삭제

    @Test
    @DisplayName("참여자는 남의 이슈도 수정할 수 있지만 삭제는 작성자와 관리자만 할 수 있다")
    void updateIsOpenDeleteIsNot() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId);
        long issueId = createIssue(memberToken, projectId, new IssueRequest("멤버가 만든 이슈", null, null, null, null, null));

        // 프로젝트 관리자(만든 사람)는 삭제할 수 있다
        authed(get("/api/issues/{id}", issueId), ownerToken)
                .andExpect(jsonPath("$.canDelete").value(true));

        String otherToken = addProjectMember(ownerToken, projectId);
        authed(patch("/api/issues/{id}", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("제목 고침", "내용", IssueStatus.REVIEW, IssuePriority.HIGH, null, null))), otherToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목 고침"))
                .andExpect(jsonPath("$.status").value("REVIEW"))
                .andExpect(jsonPath("$.canDelete").value(false));

        authed(delete("/api/issues/{id}", issueId), otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ISSUE_OWNER"));

        authed(delete("/api/issues/{id}", issueId), memberToken)
                .andExpect(status().isNoContent());

        authed(get("/api/issues/{id}", issueId), ownerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    }

    @Test
    @DisplayName("프로젝트를 지우면 그 안의 이슈도 함께 사라진다")
    void deletingProjectRemovesIssues() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long issueId = createIssue(token, projectId, new IssueRequest("사라질 이슈", null, null, null, null, null));

        authed(delete("/api/projects/{id}", projectId), token).andExpect(status().isNoContent());

        authed(get("/api/issues/{id}", issueId), token)
                .andExpect(status().isNotFound());
    }
}
