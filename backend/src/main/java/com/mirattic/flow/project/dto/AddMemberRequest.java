package com.mirattic.flow.project.dto;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotNull(message = "추가할 사용자를 선택해주세요.") Long userId) {
}
