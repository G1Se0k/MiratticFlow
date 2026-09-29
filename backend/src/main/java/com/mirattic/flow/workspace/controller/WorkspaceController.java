package com.mirattic.flow.workspace.controller;

import com.mirattic.flow.global.config.SocketExpiry;
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
    private final SocketExpiry socketExpiry;

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
        // 이미 구독 중인 채팅은 권한을 다시 보지 않는다: 연결을 끊어, 다시 붙을 때 구독 권한을 새로 검사하게 한다.
        socketExpiry.closeUser(authUser.id());
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    public MemberResponse changeRole(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId,
                                     @PathVariable Long userId, @Valid @RequestBody RoleRequest request) {
        MemberResponse member = workspaceService.changeRole(workspaceId, authUser.id(), userId, request.role());
        // 관리자에서 내려오면 참여하지 않은 프로젝트의 채팅 권한이 사라진다: 열린 구독을 끝내 다시 검사하게 한다.
        socketExpiry.closeUser(userId);
        return member;
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long workspaceId,
                             @PathVariable Long userId) {
        workspaceService.removeMember(workspaceId, authUser.id(), userId);
        socketExpiry.closeUser(userId); // 이미 열린 채팅 구독도 끝낸다 (leave 와 같은 이유)
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
