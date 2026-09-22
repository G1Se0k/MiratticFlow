package com.mirattic.flow.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceRequest(
        @NotBlank(message = "워크스페이스 이름을 입력해주세요.") @Size(max = 50) String name,
        @Size(max = 200) String description) {
}
