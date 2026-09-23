package com.mirattic.flow.chat;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.chat.dto.TopicRequest;
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

/** 주제 CRUD 권한과 시스템 메시지가 실제로 쌓이는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TopicApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private String newUserToken() throws Exception {
        String email = "t" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
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

    private long projectChatId(String token, long projectId) throws Exception {
        return id(authed(get("/api/projects/{id}/chat", projectId), token)
                .andReturn().getResponse().getContentAsString());
    }

    private long createIssue(String token, long projectId, String title) throws Exception {
        return id(authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest(title, null, null, null, null, null))), token)
                .andReturn().getResponse().getContentAsString());
    }

    private String addProjectMember(String ownerToken, long projectId) throws Exception {
        String projectBody = authed(get("/api/projects/{id}", projectId), ownerToken)
                .andReturn().getResponse().getContentAsString();
        long workspaceId = objectMapper.readTree(projectBody).get("workspaceId").asLong();
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

    // ----------------------------------------------------------------

    @Test
    @DisplayName("프로젝트를 만들면 프로젝트 채팅이 하나 생기고, 지울 수 없다")
    void projectChatIsCreated() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);

        authed(get("/api/projects/{id}/chat", projectId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("프로젝트 채팅"))
                .andExpect(jsonPath("$.issueId").doesNotExist())
                .andExpect(jsonPath("$.canManage").value(false));
    }

    @Test
    @DisplayName("참여자는 주제를 만들 수 있고, 만든 사람이 아니면 남의 주제를 고칠 수 없다")
    void createAndManage() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);
        String memberToken = addProjectMember(ownerToken, projectId);

        long issueId = createIssue(ownerToken, projectId, "로그인 오류");
        long topicId = id(authed(post("/api/issues/{id}/topics", issueId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TopicRequest("배포", "릴리스 일정"))), memberToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("배포"))
                .andReturn().getResponse().getContentAsString());

        String otherToken = addProjectMember(ownerToken, projectId);
        authed(patch("/api/topics/{id}", topicId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new TopicRequest("가로채기", null))), otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TOPIC_MANAGER"));

        // 프로젝트 관리자는 남의 주제도 고칠 수 있다
        authed(patch("/api/topics/{id}", topicId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new TopicRequest("배포 논의", null))), ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("배포 논의"));
    }

    @Test
    @DisplayName("프로젝트 채팅은 삭제할 수 없고, 이슈 주제는 삭제된다")
    void cannotDeleteProjectChat() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);

        authed(delete("/api/topics/{id}", projectChatId(token, projectId)), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_CHAT_FIXED"));

        long issueId = createIssue(token, projectId, "이슈");
        long topicId = id(authed(post("/api/issues/{id}/topics", issueId)
                .contentType(MediaType.APPLICATION_JSON).content(json(new TopicRequest("배포", null))), token)
                .andReturn().getResponse().getContentAsString());

        authed(delete("/api/topics/{id}", topicId), token).andExpect(status().isNoContent());
        authed(get("/api/issues/{id}/topics", issueId), token)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("이슈를 삭제하면 그 안의 주제와 메시지도 함께 사라진다")
    void deletingIssueRemovesItsTopics() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long issueId = createIssue(token, projectId, "이슈");
        long topicId = id(authed(post("/api/issues/{id}/topics", issueId)
                .contentType(MediaType.APPLICATION_JSON).content(json(new TopicRequest("배포", null))), token)
                .andReturn().getResponse().getContentAsString());

        authed(delete("/api/issues/{id}", issueId), token).andExpect(status().isNoContent());
        authed(get("/api/topics/{id}/messages", topicId), token)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 주제도 메시지도 볼 수 없다")
    void outsiderIsBlocked() throws Exception {
        String ownerToken = newUserToken();
        long projectId = setUpProject(ownerToken);
        long topicId = projectChatId(ownerToken, projectId);
        String outsider = newUserToken();

        authed(get("/api/projects/{id}/chat", projectId), outsider)
                .andExpect(status().isForbidden());
        authed(get("/api/topics/{id}/messages", topicId), outsider)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    @DisplayName("이슈 등록과 상태 변경이 프로젝트 채팅에 시스템 메시지로 남는다")
    void systemMessagesAreRecorded() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long topicId = projectChatId(token, projectId);

        long issueId = createIssue(token, projectId, "로그인 오류");

        authed(patch("/api/issues/{id}/status", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.IN_PROGRESS))), token)
                .andExpect(status().isOk());

        authed(get("/api/topics/{id}/messages", topicId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$[0].senderName").doesNotExist())
                .andExpect(jsonPath("$[0].content").value("테스터님이 ISSUE-1를 등록했습니다."))
                .andExpect(jsonPath("$[1].content").value("테스터님이 ISSUE-1 상태를 IN_PROGRESS로 변경했습니다."));
    }

    @Test
    @DisplayName("같은 상태로 다시 바꾸면 시스템 메시지가 쌓이지 않는다")
    void noMessageWhenStatusUnchanged() throws Exception {
        String token = newUserToken();
        long projectId = setUpProject(token);
        long topicId = projectChatId(token, projectId);
        long issueId = createIssue(token, projectId, "이슈");

        authed(patch("/api/issues/{id}/status", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueStatusRequest(IssueStatus.TODO))), token)
                .andExpect(status().isOk());

        authed(get("/api/topics/{id}/messages", topicId), token)
                .andExpect(jsonPath("$.length()").value(1)); // 등록 메시지 하나뿐
    }
}
