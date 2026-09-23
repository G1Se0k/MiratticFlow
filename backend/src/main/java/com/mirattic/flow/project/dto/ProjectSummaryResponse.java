package com.mirattic.flow.project.dto;

import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.entity.ProjectStatus;

import java.time.LocalDateTime;

/** 목록용. 상세와 달리 참여자 수만 있고 멤버 명단은 없다. */
public record ProjectSummaryResponse(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        String createdByName,
        long memberCount,
        LocalDateTime createdAt) {

    public static ProjectSummaryResponse of(Project project, long memberCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedBy().getName(),
                memberCount,
                project.getCreatedAt());
    }
}
