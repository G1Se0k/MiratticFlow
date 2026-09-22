package com.mirattic.flow.workspace.dto;

import com.mirattic.flow.workspace.entity.Workspace;
import com.mirattic.flow.workspace.entity.WorkspaceRole;

import java.time.LocalDateTime;

/** myRole 은 "지금 보고 있는 사용자"의 역할이다. 프론트가 관리 UI 노출 여부를 판단하는 데 쓴다. */
public record WorkspaceResponse(
        Long id, String name, String description, WorkspaceRole myRole, LocalDateTime createdAt) {

    public static WorkspaceResponse of(Workspace workspace, WorkspaceRole myRole) {
        return new WorkspaceResponse(
                workspace.getId(), workspace.getName(), workspace.getDescription(), myRole, workspace.getCreatedAt());
    }
}
