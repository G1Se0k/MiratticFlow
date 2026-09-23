package com.mirattic.flow.project;

import com.mirattic.flow.project.dto.AddMemberRequest;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.project.entity.ProjectStatus;
import com.mirattic.flow.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 프로젝트 접근 권한(참여자 / 워크스페이스 관리자 / 만든 사람)이 규칙대로 갈리는지 확인한다. */
class ProjectApiTest extends ApiTestSupport {

    // ---------------------------------------------------------------- 헬퍼

    /** 워크스페이스에 새 사용자를 참여시키고 그 사용자의 토큰을 돌려준다. */
    private String joinNewMember(String ownerToken, long workspaceId) throws Exception {
        String body = authed(get("/api/workspaces/{id}/invite-code", workspaceId), ownerToken)
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(body).get("code").asString();

        String memberToken = newUserToken();
        authed(post("/api/invites/{code}/accept", code), memberToken).andExpect(status().isOk());
        return memberToken;
    }



    // ---------------------------------------------------------------- 생성 / 조회

    @Test
    @DisplayName("프로젝트를 만들면 만든 사람이 참여자로 들어가고 목록에 보인다")
    void createProject() throws Exception {
        String token = newUserToken();
        long workspaceId = createWorkspace(token);
        long projectId = createProject(token, workspaceId, "웹 리뉴얼");

        authed(get("/api/workspaces/{id}/projects", workspaceId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("웹 리뉴얼"))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        authed(get("/api/projects/{id}/members", projectId), token)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 프로젝트를 만들 수 없다")
    void createByOutsider() throws Exception {
        long workspaceId = createWorkspace(newUserToken());

        authed(post("/api/workspaces/{id}/projects", workspaceId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProjectRequest("몰래", null, null))), newUserToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
    }

    @Test
    @DisplayName("참여하지 않은 프로젝트는 일반 멤버의 목록에 보이지 않고 상세도 막힌다")
    void nonMemberCannotSeeProject() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken);
        long projectId = createProject(ownerToken, workspaceId, "비밀 프로젝트");
        String memberToken = joinNewMember(ownerToken, workspaceId);

        authed(get("/api/workspaces/{id}/projects", workspaceId), memberToken)
                .andExpect(jsonPath("$.length()").value(0));

        authed(get("/api/projects/{id}", projectId), memberToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
    }

    @Test
    @DisplayName("참여자로 추가되면 상세가 열리고, 만든 사람이 아니므로 수정은 막힌다")
    void memberCanViewButNotManage() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken);
        String memberToken = joinNewMember(ownerToken, workspaceId);
        // 프로젝트는 일반 멤버가 만들고, 워크스페이스 관리자를 참여자로 넣는다.
        long projectId = createProject(memberToken, workspaceId, "멤버의 프로젝트");

        String otherToken = joinNewMember(ownerToken, workspaceId);
        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(userIdOf(otherToken)))), memberToken)
                .andExpect(status().isCreated());

        authed(get("/api/projects/{id}", projectId), otherToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false));

        authed(patch("/api/projects/{id}", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProjectRequest("가로채기", null, null))), otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MANAGER"));
    }

    @Test
    @DisplayName("워크스페이스 관리자는 참여하지 않은 프로젝트도 보고 지울 수 있다")
    void workspaceOwnerManagesAnyProject() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken);
        String memberToken = joinNewMember(ownerToken, workspaceId);
        long projectId = createProject(memberToken, workspaceId, "멤버의 프로젝트");

        authed(get("/api/projects/{id}", projectId), ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(true));

        authed(delete("/api/projects/{id}", projectId), ownerToken)
                .andExpect(status().isNoContent());

        authed(get("/api/projects/{id}", projectId), memberToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- 참여자

    @Test
    @DisplayName("같은 사람을 두 번 추가할 수 없고, 워크스페이스 밖의 사람은 추가할 수 없다")
    void addMemberRules() throws Exception {
        String ownerToken = newUserToken();
        long workspaceId = createWorkspace(ownerToken);
        long projectId = createProject(ownerToken, workspaceId, "협업");
        String memberToken = joinNewMember(ownerToken, workspaceId);
        long memberUserId = userIdOf(memberToken);

        MockHttpServletRequestBuilder add = post("/api/projects/{id}/members", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(memberUserId)));
        authed(add, ownerToken).andExpect(status().isCreated());

        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(memberUserId))), ownerToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PROJECT_MEMBER"));

        long outsiderId = userIdOf(newUserToken());
        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddMemberRequest(outsiderId))), ownerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("프로젝트를 보관하면 상태만 바뀌고 목록에 남는다")
    void archiveProject() throws Exception {
        String token = newUserToken();
        long workspaceId = createWorkspace(token);
        long projectId = createProject(token, workspaceId, "구버전");

        authed(patch("/api/projects/{id}", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProjectRequest("구버전", "보관", ProjectStatus.ARCHIVED))), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        authed(get("/api/workspaces/{id}/projects", workspaceId), token)
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
    }
}
