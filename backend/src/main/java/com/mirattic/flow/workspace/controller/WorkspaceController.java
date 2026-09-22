package com.mirattic.flow.workspace.controller;

import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.workspace.dto.*;
import com.mirattic.flow.workspace.service.WorkspaceInviteService;
import com.mirattic.flow.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceInviteService inviteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@AuthenticationPrincipal AuthUser authUser,
                                    @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.create(authUser.id(), request);
    }

    @GetMapping
    public List<WorkspaceResponse> findMine(@AuthenticationPrincipal AuthUser authUser) {
        return workspaceService.findMine(authUser.id());
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse findOne(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        return workspaceService.findOne(workspaceId, authUser.id());
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceResponse update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId,
                                    @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.update(workspaceId, authUser.id(), request);
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        workspaceService.delete(workspaceId, authUser.id());
    }

    // ---------------------------------------------------------------- 멤버

    @GetMapping("/{workspaceId}/members")
    public List<MemberResponse> findMembers(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        return workspaceService.findMembers(workspaceId, authUser.id());
    }

    /** "me" 는 아래 {userId} 보다 먼저 매칭된다 (구체적인 경로가 우선). */
    @DeleteMapping("/{workspaceId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        workspaceService.leave(workspaceId, authUser.id());
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    public MemberResponse changeRole(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId,
                                     @PathVariable Long userId, @Valid @RequestBody RoleRequest request) {
        return workspaceService.changeRole(workspaceId, authUser.id(), userId, request.role());
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId,
                             @PathVariable Long userId) {
        workspaceService.removeMember(workspaceId, authUser.id(), userId);
    }

    // ---------------------------------------------------------------- 초대 발급

    @PostMapping("/{workspaceId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse createLink(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        return inviteService.createLink(workspaceId, authUser.id());
    }

    @GetMapping("/{workspaceId}/invites")
    public List<InviteResponse> findLinks(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        return inviteService.findLinks(workspaceId, authUser.id());
    }

    @GetMapping("/{workspaceId}/invite-code")
    public InviteResponse getJoinCode(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        return inviteService.getJoinCode(workspaceId, authUser.id());
    }

    @PostMapping("/{workspaceId}/invite-code")
    public InviteResponse regenerateJoinCode(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId) {
        return inviteService.regenerateJoinCode(workspaceId, authUser.id());
    }
}
