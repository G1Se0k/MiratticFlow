package com.mirattic.flow.workspace.dto;

import com.mirattic.flow.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(@NotNull WorkspaceRole role) {
}
