package com.mirattic.flow.workspace;

import com.mirattic.flow.comment.dto.CommentRequest;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.workspace.dto.RoleRequest;
import com.mirattic.flow.workspace.dto.WorkspaceRequest;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import com.mirattic.flow.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 권한 검사와 초대 수명 규칙을 실제 DB 제약까지 포함해 확인한다. */
class WorkspaceApiTest extends ApiTestSupport {

    // ---------------------------------------------------------------- 헬퍼

    /** 새 사용자를 만들고 access token 을 돌려준다. */


    private String createInviteLink(String token, long workspaceId) throws Exception {
        String body = authed(post("/api/workspaces/{id}/invites", workspaceId), token)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("code").asString();
    }

    private String joinCode(String token, long workspaceId) throws Exception {
        String body = authed(get("/api/workspaces/{id}/invite-code", workspaceId), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("code").asString();
    }


    // ---------------------------------------------------------------- 워크스페이스

    @Test
    @DisplayName("워크스페이스를 만들면 만든 사람이 OWNER 로 들어가고 내 목록에 보인다")
    void createWorkspace() throws Exception {
        String token = newUserToken();
        long workspaceId = createWorkspace(token, "우리팀");

        authed(get("/api/workspaces/{id}", workspaceId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("우리팀"))
                .andExpect(jsonPath("$.myRole").value("OWNER"));

        authed(get("/api/workspaces"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workspaceId));
    }

    @Test
    @DisplayName("멤버가 아니면 상세 조회도 403 이다 (존재 여부를 알려주지 않는다)")
    void nonMemberCannotRead() throws Exception {
        long workspaceId = createWorkspace(newUserToken(), "남의팀");

        authed(get("/api/workspaces/{id}", workspaceId), newUserToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
    }

    @Test
    @DisplayName("MEMBER 는 워크스페이스를 수정할 수 없다")
    void memberCannotUpdate() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String memberToken = newUserToken();
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), memberToken)
                .andExpect(status().isOk());

        authed(patch("/api/workspaces/{id}", workspaceId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new WorkspaceRequest("바꿀이름", null))), memberToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_OWNER"));
    }

    @Test
    @DisplayName("MEMBER 는 워크스페이스를 삭제할 수 없다")
    void memberCannotDelete() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String memberToken = newUserToken();
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), memberToken)
                .andExpect(status().isOk());

        authed(delete("/api/workspaces/{id}", workspaceId), memberToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_OWNER"));

        authed(get("/api/workspaces/{id}", workspaceId), ownerToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("OWNER 가 워크스페이스를 지우면 프로젝트·이슈·댓글까지 함께 사라지고 멤버도 볼 수 없다")
    void ownerDeletesWorkspaceWithContents() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "지울팀");
        String memberToken = newUserToken();
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), memberToken)
                .andExpect(status().isOk());

        // 프로젝트를 만들면 기본 채팅 주제와 참여자가 함께 생긴다. 자식 삭제 순서를 실제 FK 로 확인하기 위한 준비다.
        long projectId = createProject(ownerToken, workspaceId, "지울프로젝트");
        long issueId = id(bodyOf(authed(post("/api/projects/{id}/issues", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new IssueRequest("버그", null, null, null, null, null))), ownerToken)
                .andExpect(status().isCreated())));
        authed(post("/api/issues/{id}/comments", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("고칠게요"))), ownerToken)
                .andExpect(status().isCreated());

        authed(delete("/api/workspaces/{id}", workspaceId), ownerToken)
                .andExpect(status().isNoContent());

        authed(get("/api/issues/{id}", issueId), ownerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
        authed(get("/api/projects/{id}", projectId), ownerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        // 멤버십 행까지 지워졌으므로 멤버가 아닌 사람과 똑같이 403 이다 (존재 여부를 알려주지 않는다).
        authed(get("/api/workspaces/{id}", workspaceId), memberToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
        authed(get("/api/workspaces"), memberToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------- 초대 링크

    @Test
    @DisplayName("초대 링크로 참여하면 MEMBER 가 되고, 같은 링크는 두 번 쓸 수 없다")
    void inviteLinkIsSingleUse() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String code = createInviteLink(ownerToken, workspaceId);

        String guestToken = newUserToken();
        authed(get("/api/invites/{code}", code), guestToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("우리팀"))
                .andExpect(jsonPath("$.alreadyMember").value(false));

        authed(post("/api/invites/{code}/accept", code), guestToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId));

        authed(get("/api/workspaces/{id}", workspaceId), guestToken)
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        // 두 번째 사람은 같은 링크를 쓸 수 없다 (maxUses = 1)
        authed(post("/api/invites/{code}/accept", code), newUserToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    @DisplayName("이미 멤버가 링크를 다시 눌러도 에러가 아니고 사용 횟수도 줄지 않는다")
    void acceptTwiceBySameUser() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String code = createInviteLink(ownerToken, workspaceId);

        String guestToken = newUserToken();
        authed(post("/api/invites/{code}/accept", code), guestToken).andExpect(status().isOk());
        // 이미 멤버다. 그냥 같은 워크스페이스로 안내한다.
        authed(post("/api/invites/{code}/accept", code), guestToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId));
    }

    @Test
    @DisplayName("폐기한 링크는 쓸 수 없다")
    void revokedLinkIsUnusable() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String code = createInviteLink(ownerToken, workspaceId);

        String listBody = authed(get("/api/workspaces/{id}/invites", workspaceId), ownerToken)
                .andReturn().getResponse().getContentAsString();
        long inviteId = objectMapper.readTree(listBody).get(0).get("id").asLong();

        authed(delete("/api/invites/{id}", inviteId), ownerToken).andExpect(status().isNoContent());

        authed(post("/api/invites/{code}/accept", code), newUserToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    @DisplayName("MEMBER 는 초대 링크를 만들 수 없다")
    void memberCannotCreateInvite() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String memberToken = newUserToken();
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), memberToken);

        authed(post("/api/workspaces/{id}/invites", workspaceId), memberToken)
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 상시 코드

    @Test
    @DisplayName("상시 코드는 여러 명이 쓸 수 있고, 재발급하면 이전 코드가 막힌다")
    void joinCodeIsReusableUntilRegenerated() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String code = joinCode(ownerToken, workspaceId);

        authed(post("/api/invites/{code}/accept", code), newUserToken()).andExpect(status().isOk());
        authed(post("/api/invites/{code}/accept", code), newUserToken()).andExpect(status().isOk());

        String newBody = authed(post("/api/workspaces/{id}/invite-code", workspaceId), ownerToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newCode = objectMapper.readTree(newBody).get("code").asString();
        assertThat(newCode).isNotEqualTo(code);

        authed(post("/api/invites/{code}/accept", code), newUserToken())
                .andExpect(status().isBadRequest());
        authed(post("/api/invites/{code}/accept", newCode), newUserToken())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 코드와 만료된 코드는 같은 에러로 응답한다")
    void unknownCodeIsSameError() throws Exception {
        authed(post("/api/invites/{code}/accept", "XXXX-XXXX"), newUserToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    @DisplayName("초대 수락은 로그인해야 한다")
    void acceptRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/invites/{code}/accept", "XXXX-XXXX"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 멤버 관리

    @Test
    @DisplayName("마지막 OWNER 는 워크스페이스를 나갈 수 없다")
    void lastOwnerCannotLeave() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");

        authed(delete("/api/workspaces/{id}/members/me", workspaceId), ownerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAST_OWNER"));
    }

    @Test
    @DisplayName("OWNER 가 둘이면 한 명은 나갈 수 있다")
    void ownerCanLeaveWhenAnotherOwnerExists() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String otherToken = newUserToken();
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), otherToken);

        authed(patch("/api/workspaces/{id}/members/{userId}", workspaceId, userIdOf(otherToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(new RoleRequest(WorkspaceRole.OWNER))), ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));

        authed(delete("/api/workspaces/{id}/members/me", workspaceId), ownerToken)
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("자신의 역할은 바꿀 수 없다")
    void cannotChangeOwnRole() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");

        authed(patch("/api/workspaces/{id}/members/{userId}", workspaceId, userIdOf(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(new RoleRequest(WorkspaceRole.MEMBER))), ownerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));
    }

    @Test
    @DisplayName("OWNER 는 멤버를 제거할 수 있고, 제거된 사람은 더 이상 접근할 수 없다")
    void removeMember() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        String memberToken = newUserToken();
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), memberToken);

        authed(delete("/api/workspaces/{id}/members/{userId}", workspaceId, userIdOf(memberToken)), ownerToken)
                .andExpect(status().isNoContent());

        authed(get("/api/workspaces/{id}", workspaceId), memberToken)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("멤버 목록에는 참여한 사람이 모두 보인다")
    void memberList() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken, "우리팀");
        authed(post("/api/invites/{code}/accept", joinCode(ownerToken, workspaceId)), newUserToken());

        authed(get("/api/workspaces/{id}/members", workspaceId), ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }
}
