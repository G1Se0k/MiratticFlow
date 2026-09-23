package com.mirattic.flow.project.dto;

import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.entity.ProjectStatus;

import java.time.LocalDateTime;

/**
 * canManage 는 "지금 보고 있는 사용자가 이 프로젝트를 수정·삭제할 수 있는가".
 * 프론트가 권한 규칙을 다시 계산하지 않도록 서버가 판단해서 내려준다.
 */
public record ProjectResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        ProjectStatus status,
        String createdByName,
        boolean canManage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectResponse of(Project project, boolean canManage) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspace().getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedBy().getName(),
                canManage,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
