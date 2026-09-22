package com.mirattic.flow.workspace.controller;

import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.workspace.dto.InvitePreviewResponse;
import com.mirattic.flow.workspace.dto.JoinResponse;
import com.mirattic.flow.workspace.service.WorkspaceInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 초대를 받은 쪽에서 쓰는 API. 아직 멤버가 아니지만 로그인은 되어 있어야 한다. */
@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final WorkspaceInviteService inviteService;

    @GetMapping("/{code}")
    public InvitePreviewResponse preview(@AuthenticationPrincipal AuthUser authUser, @PathVariable String code) {
        return inviteService.preview(code, authUser.id());
    }

    @PostMapping("/{code}/accept")
    public JoinResponse accept(@AuthenticationPrincipal AuthUser authUser, @PathVariable String code) {
        return inviteService.accept(code, authUser.id());
    }

    @DeleteMapping("/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeLink(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long inviteId) {
        inviteService.revokeLink(inviteId, authUser.id());
    }
}
