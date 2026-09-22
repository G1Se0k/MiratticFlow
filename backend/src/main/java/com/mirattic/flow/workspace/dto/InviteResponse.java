package com.mirattic.flow.workspace.dto;

import com.mirattic.flow.workspace.entity.InviteType;
import com.mirattic.flow.workspace.entity.WorkspaceInvite;

import java.time.LocalDateTime;

public record InviteResponse(
        Long id, String code, InviteType type, LocalDateTime expiresAt,
        Integer maxUses, int usedCount, LocalDateTime createdAt) {

    public static InviteResponse from(WorkspaceInvite invite) {
        return new InviteResponse(
                invite.getId(), invite.getCode(), invite.getType(), invite.getExpiresAt(),
                invite.getMaxUses(), invite.getUsedCount(), invite.getCreatedAt());
    }
}
