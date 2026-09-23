package com.mirattic.flow.project.controller;

import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.project.dto.AddMemberRequest;
import com.mirattic.flow.project.dto.ProjectMemberResponse;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.project.dto.ProjectResponse;
import com.mirattic.flow.project.dto.ProjectSummaryResponse;
import com.mirattic.flow.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ---------------------------------------------------------------- 프로젝트
    // 생성·목록은 워크스페이스에 속하고, 나머지는 프로젝트 id 하나로 찾아간다.

    @PostMapping("/api/workspaces/{workspaceId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId,
                                  @Valid @RequestBody ProjectRequest request) {
        return projectService.create(workspaceId, authUser.id(), request);
    }

    @GetMapping("/api/workspaces/{workspaceId}/projects")
    public List<ProjectSummaryResponse> findAll(@AuthenticationPrincipal AuthUser authUser,
                                                @PathVariable Long workspaceId) {
        return projectService.findAll(workspaceId, authUser.id());
    }

    @GetMapping("/api/projects/{projectId}")
    public ProjectResponse findOne(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId) {
        return projectService.findOne(projectId, authUser.id());
    }

    @PatchMapping("/api/projects/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId,
                                  @Valid @RequestBody ProjectRequest request) {
        return projectService.update(projectId, authUser.id(), request);
    }

    @DeleteMapping("/api/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId) {
        projectService.delete(projectId, authUser.id());
    }

    // ---------------------------------------------------------------- 참여자

    @GetMapping("/api/projects/{projectId}/members")
    public List<ProjectMemberResponse> findMembers(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long projectId) {
        return projectService.findMembers(projectId, authUser.id());
    }

    @PostMapping("/api/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse addMember(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId,
                                           @Valid @RequestBody AddMemberRequest request) {
        return projectService.addMember(projectId, authUser.id(), request.userId());
    }

    @DeleteMapping("/api/projects/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId,
                             @PathVariable Long userId) {
        projectService.removeMember(projectId, authUser.id(), userId);
    }
}
