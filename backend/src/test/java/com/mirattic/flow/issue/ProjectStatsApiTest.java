package com.mirattic.flow.issue;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.issue.dto.IssueStatusRequest;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 집계가 맞는지, 0건인 값도 빠지지 않는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProjectStatsApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private String newUserToken() throws Exception {
        String email = "s" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
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

    private long id(String body) {
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long userIdOf(String token) throws Exception {
        return id(authed(get("/api/users/me"), token).andReturn().getResponse().getContentAsString());
    }

    private long setUpProject(String token) throws Exception {
        long workspaceId = id(authed(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WorkspaceRequest("팀", null))), token)
                .andReturn().getResponse().getContentAsString());
        return id(authed(post("/api/workspaces/{id}/projects", workspaceId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProjectRequest("프로젝트", null, null))), token)
                .andReturn().getResponse().getContentAsString());
    }

    private long createIssue(String token, long projectId, String title,
                             IssuePriority priority, Long assigneeId) throws Exception {
        return id(authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest(title, null, null, priority, assigneeId, null))), token)
                .andReturn().getResponse().getContentAsString());
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("이슈가 없어도 상태·우선순위 네 칸이 0으로 모두 나온다")
    void emptyProjectStillHasEveryBucket() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);

        authed(get("/api/projects/{id}/stats", projectId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.byStatus.length()").value(4))
                .andExpect(jsonPath("$.byStatus[0].status").value("TODO"))
                .andExpect(jsonPath("$.byStatus[0].count").value(0))
                .andExpect(jsonPath("$.byPriority.length()").value(4))
                .andExpect(jsonPath("$.byAssignee.length()").value(0));
    }

    @Test
    @DisplayName("상태별·우선순위별 집계와 카드 숫자가 맞는다")
    void countsByStatusAndPriority() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long userId = userIdOf(token);

        long first = createIssue(token, projectId, "하나", IssuePriority.URGENT, userId);
        createIssue(token, projectId, "둘", IssuePriority.URGENT, null);
        long third = createIssue(token, projectId, "셋", IssuePriority.LOW, userId);

        authed(patch("/api/issues/{id}/status", first).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.IN_PROGRESS))), token);
        authed(patch("/api/issues/{id}/status", third).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.DONE))), token);

        authed(get("/api/projects/{id}/stats", projectId), token)
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.mine").value(2))
                .andExpect(jsonPath("$.byStatus[?(@.status == 'TODO')].count").value(1))
                .andExpect(jsonPath("$.byPriority[?(@.priority == 'URGENT')].count").value(2))
                .andExpect(jsonPath("$.byPriority[?(@.priority == 'MEDIUM')].count").value(0));
    }

    @Test
    @DisplayName("담당자 없는 이슈도 한 칸으로 집계된다")
    void unassignedIsCounted() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long userId = userIdOf(token);

        createIssue(token, projectId, "맡은 것", IssuePriority.MEDIUM, userId);
        createIssue(token, projectId, "아무도 안 맡은 것", IssuePriority.MEDIUM, null);
        createIssue(token, projectId, "또 안 맡은 것", IssuePriority.MEDIUM, null);

        authed(get("/api/projects/{id}/stats", projectId), token)
                .andExpect(jsonPath("$.byAssignee.length()").value(2))
                // 많은 순으로 정렬되므로 담당자 없음(2건)이 먼저 온다
                .andExpect(jsonPath("$.byAssignee[0].userId").doesNotExist())
                .andExpect(jsonPath("$.byAssignee[0].count").value(2))
                .andExpect(jsonPath("$.byAssignee[1].userId").value(userId));
    }

    @Test
    @DisplayName("최근 활동은 시스템 메시지를 최신순으로 돌려준다")
    void recentActivity() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long issueId = createIssue(token, projectId, "로그인 오류", IssuePriority.HIGH, null);

        authed(patch("/api/issues/{id}/status", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.REVIEW))), token);

        authed(get("/api/projects/{id}/stats", projectId), token)
                .andExpect(jsonPath("$.recentActivity.length()").value(2))
                .andExpect(jsonPath("$.recentActivity[0].content")
                        .value("테스터님이 ISSUE-1 상태를 REVIEW로 변경했습니다."))
                .andExpect(jsonPath("$.recentActivity[1].content").value("테스터님이 ISSUE-1를 등록했습니다."));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 통계도 볼 수 없다")
    void outsiderIsBlocked() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);

        authed(get("/api/projects/{id}/stats", projectId), newUserToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }
}
