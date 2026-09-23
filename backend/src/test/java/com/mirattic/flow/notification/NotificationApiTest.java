package com.mirattic.flow.notification;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.comment.dto.CommentRequest;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.issue.dto.IssueStatusRequest;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림이 "알아야 할 사람에게만" 가는지, 읽음 처리가 내 것에만 먹히는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private String newUserToken(String name) throws Exception {
        String email = "n" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest(email, "password123", name))));
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
                .content(json(new ProjectRequest("웹 리뉴얼", null, null))), token)
                .andReturn().getResponse().getContentAsString());
    }

    /** 워크스페이스에 초대해 프로젝트 참여자로 넣은 새 사용자의 토큰. */
    private String addProjectMember(String ownerToken, long projectId, String name) throws Exception {
        String projectBody = authed(get("/api/projects/{id}", projectId), ownerToken)
                .andReturn().getResponse().getContentAsString();
        long workspaceId = objectMapper.readTree(projectBody).get("workspaceId").asLong();
        String codeBody = authed(get("/api/workspaces/{id}/invite-code", workspaceId), ownerToken)
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(codeBody).get("code").asString();

        String token = newUserToken(name);
        authed(post("/api/invites/{code}/accept", code), token).andExpect(status().isOk());
        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(userIdOf(token)))), ownerToken)
                .andExpect(status().isCreated());
        return token;
    }

    private long createIssue(String token, long projectId, String title, Long assigneeId) throws Exception {
        return id(authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest(title, null, null, null, assigneeId, null))), token)
                .andReturn().getResponse().getContentAsString());
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("프로젝트에 추가되면 알림이 간다")
    void notifiedOnProjectJoin() throws Exception {
        String ownerToken = newUserToken("오너");
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId, "게스트");

        authed(get("/api/notifications"), memberToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PROJECT_JOINED"))
                .andExpect(jsonPath("$[0].content").value("웹 리뉴얼 프로젝트에 참여하게 되었습니다."))
                .andExpect(jsonPath("$[0].link").value("/projects/" + projectId))
                .andExpect(jsonPath("$[0].read").value(false));

        // 추가한 사람에게는 가지 않는다
        authed(get("/api/notifications"), ownerToken)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("담당자로 지정되면 알림이 가고, 본인이 본인을 지정하면 가지 않는다")
    void notifiedOnAssignment() throws Exception {
        String ownerToken = newUserToken("오너");
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId, "게스트");
        long memberId = userIdOf(memberToken);
        long ownerId = userIdOf(ownerToken);

        createIssue(ownerToken, projectId, "로그인 오류", memberId);

        authed(get("/api/notifications"), memberToken)
                .andExpect(jsonPath("$.length()").value(2)) // 참여 알림 + 담당자 지정
                .andExpect(jsonPath("$[0].type").value("ISSUE_ASSIGNED"))
                .andExpect(jsonPath("$[0].content").value("ISSUE-1 담당자로 지정되었습니다: 로그인 오류"));

        // 오너가 자기 자신을 담당자로 지정하면 자기에게는 알림이 없다
        createIssue(ownerToken, projectId, "내가 할 일", ownerId);
        authed(get("/api/notifications"), ownerToken)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("상태를 바꾸면 담당자와 작성자에게 가고, 바꾼 본인에게는 가지 않는다")
    void notifiedOnStatusChange() throws Exception {
        String ownerToken = newUserToken("오너");
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId, "게스트");
        long issueId = createIssue(ownerToken, projectId, "로그인 오류", userIdOf(memberToken));

        // 담당자(게스트)가 상태를 바꾼다 → 작성자(오너)에게만 간다
        authed(patch("/api/issues/{id}/status", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.IN_PROGRESS))), memberToken)
                .andExpect(status().isOk());

        authed(get("/api/notifications"), ownerToken)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ISSUE_STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].content").value("ISSUE-1 상태가 IN_PROGRESS로 바뀌었습니다."));

        // 게스트는 담당자지만 본인이 바꿨으므로 상태 알림이 늘지 않는다 (참여 + 담당자 지정 = 2건 그대로)
        authed(get("/api/notifications"), memberToken)
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("댓글은 이슈 작성자와 담당자에게 알리고, 같은 사람이면 한 번만 간다")
    void notifiedOnComment() throws Exception {
        String ownerToken = newUserToken("오너");
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId, "게스트");

        // 오너가 작성자이자 담당자인 이슈
        long issueId = createIssue(ownerToken, projectId, "로그인 오류", userIdOf(ownerToken));

        authed(post("/api/issues/{id}/comments", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("확인해볼게요"))), memberToken)
                .andExpect(status().isCreated());

        // 작성자 = 담당자라도 한 건만
        authed(get("/api/notifications"), ownerToken)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("COMMENT_ADDED"))
                .andExpect(jsonPath("$[0].content").value("게스트님이 ISSUE-1에 댓글을 남겼습니다."));

        // 본인이 쓴 댓글은 본인에게 오지 않는다
        authed(get("/api/notifications"), memberToken)
                .andExpect(jsonPath("$.length()").value(1)); // 참여 알림뿐
    }

    @Test
    @DisplayName("읽음 처리와 안 읽은 수")
    void readAndCount() throws Exception {
        String ownerToken = newUserToken("오너");
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId, "게스트");
        createIssue(ownerToken, projectId, "로그인 오류", userIdOf(memberToken));

        authed(get("/api/notifications/unread-count"), memberToken)
                .andExpect(jsonPath("$.count").value(2));

        long first = objectMapper.readTree(
                        authed(get("/api/notifications"), memberToken).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        authed(patch("/api/notifications/{id}/read", first), memberToken)
                .andExpect(status().isNoContent());
        authed(get("/api/notifications/unread-count"), memberToken)
                .andExpect(jsonPath("$.count").value(1));

        authed(patch("/api/notifications/read-all"), memberToken)
                .andExpect(status().isNoContent());
        authed(get("/api/notifications/unread-count"), memberToken)
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("남의 알림은 읽음 처리할 수 없다")
    void cannotReadSomeoneElsesNotification() throws Exception {
        String ownerToken = newUserToken("오너");
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId, "게스트");

        long memberNotificationId = objectMapper.readTree(
                        authed(get("/api/notifications"), memberToken).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        String outsider = newUserToken("외부인");
        authed(patch("/api/notifications/{id}/read", memberNotificationId), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        // 여전히 안 읽음으로 남아 있다
        authed(get("/api/notifications/unread-count"), memberToken)
                .andExpect(jsonPath("$.count").value(1));
    }
}
