package com.mirattic.flow.workspace.dto;

import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;

import java.time.LocalDateTime;

public record MemberResponse(
        Long userId, String name, String email, WorkspaceRole role, LocalDateTime joinedAt) {

    public static MemberResponse from(WorkspaceMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(), // 소셜 가입자는 null 일 수 있다
                member.getRole(),
                member.getCreatedAt());
    }
}
