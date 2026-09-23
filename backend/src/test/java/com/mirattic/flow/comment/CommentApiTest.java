package com.mirattic.flow.comment;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.comment.dto.CommentRequest;
import com.mirattic.flow.issue.dto.IssueRequest;
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

/** 댓글 권한이 이슈 접근 권한을 그대로 따르는지, 수정/삭제 범위가 다른지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CommentApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private String newUserToken() throws Exception {
        String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
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

    private long id(String body) {
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** 워크스페이스 → 프로젝트 → 이슈까지 만들고 이슈 id 를 돌려준다. */
    private long setUpIssue(String token) throws Exception {
        long workspaceId = id(authed(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WorkspaceRequest("팀", null))), token)
                .andReturn().getResponse().getContentAsString());
        long projectId = id(authed(post("/api/workspaces/{id}/projects", workspaceId)
                .contentType(MediaType.APPLICATION_JSON).content(json(new ProjectRequest("프로젝트", null, null))), token)
                .andReturn().getResponse().getContentAsString());
        return id(authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("이슈", null, null, null, null, null))), token)
                .andReturn().getResponse().getContentAsString());
    }

    private long projectIdOfIssue(long issueId, String token) throws Exception {
        String body = authed(get("/api/issues/{id}", issueId), token).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("projectId").asLong();
    }

    /** 새 사용자를 이슈가 속한 프로젝트까지 참여시킨다. */
    private String addProjectMember(String ownerToken, long issueId) throws Exception {
        long projectId = projectIdOfIssue(issueId, ownerToken);
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

    private long writeComment(String token, long issueId, String content) throws Exception {
        return id(authed(post("/api/issues/{id}/comments", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest(content))), token)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("댓글을 쓰면 작성일 순으로 목록에 쌓인다")
    void writeAndList() throws Exception {
        String token = newUserToken();
        long issueId = setUpIssue(token);
        writeComment(token, issueId, "첫 댓글");
        writeComment(token, issueId, "둘째 댓글");

        authed(get("/api/issues/{id}/comments", issueId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$[0].authorName").value("테스터"))
                .andExpect(jsonPath("$[1].content").value("둘째 댓글"));
    }

    @Test
    @DisplayName("이슈를 볼 수 없으면 댓글도 볼 수 없다")
    void outsiderCannotReadComments() throws Exception {
        String ownerToken = newUserToken();
        long issueId = setUpIssue(ownerToken);
        writeComment(ownerToken, issueId, "비밀 이야기");

        authed(get("/api/issues/{id}/comments", issueId), newUserToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    @DisplayName("남의 댓글은 수정할 수 없다")
    void cannotEditOthersComment() throws Exception {
        String ownerToken = newUserToken();
        long issueId = setUpIssue(ownerToken);
        String memberToken = addProjectMember(ownerToken, issueId);
        long commentId = writeComment(memberToken, issueId, "멤버의 댓글");

        authed(patch("/api/comments/{id}", commentId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("가로채기"))), ownerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_COMMENT_AUTHOR"));

        authed(patch("/api/comments/{id}", commentId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("내가 고침"))), memberToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("내가 고침"));
    }

    @Test
    @DisplayName("프로젝트 관리자는 남의 댓글을 삭제할 수 있다")
    void managerCanDeleteOthersComment() throws Exception {
        String ownerToken = newUserToken();
        long issueId = setUpIssue(ownerToken);
        String memberToken = addProjectMember(ownerToken, issueId);
        long commentId = writeComment(memberToken, issueId, "지워질 댓글");

        // 관리자는 수정은 못 하고 삭제만 된다 — 플래그가 따로 내려간다
        authed(get("/api/issues/{id}/comments", issueId), ownerToken)
                .andExpect(jsonPath("$[0].canEdit").value(false))
                .andExpect(jsonPath("$[0].canDelete").value(true));

        String otherToken = addProjectMember(ownerToken, issueId);
        authed(delete("/api/comments/{id}", commentId), otherToken)
                .andExpect(status().isForbidden());

        authed(delete("/api/comments/{id}", commentId), ownerToken)
                .andExpect(status().isNoContent());

        authed(get("/api/issues/{id}/comments", issueId), ownerToken)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("이슈를 지우면 댓글도 함께 사라진다")
    void deletingIssueRemovesComments() throws Exception {
        String token = newUserToken();
        long issueId = setUpIssue(token);
        long commentId = writeComment(token, issueId, "사라질 댓글");

        authed(delete("/api/issues/{id}", issueId), token).andExpect(status().isNoContent());

        authed(patch("/api/comments/{id}", commentId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("수정 시도"))), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("빈 댓글은 저장되지 않는다")
    void blankCommentIsRejected() throws Exception {
        String token = newUserToken();
        long issueId = setUpIssue(token);

        authed(post("/api/issues/{id}/comments", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("   "))), token)
                .andExpect(status().isBadRequest());
    }
}
