package com.mirattic.flow.project.dto;

import com.mirattic.flow.project.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** status 는 생성 시엔 무시된다 (항상 ACTIVE 로 시작). 수정 시 null 이면 기존 상태를 유지한다. */
public record ProjectRequest(
        @NotBlank(message = "프로젝트 이름을 입력해주세요.") @Size(max = 50) String name,
        @Size(max = 200) String description,
        ProjectStatus status) {
}
