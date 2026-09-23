package com.mirattic.flow.issue.dto;

import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IssueResponse(
        Long id,
        Long projectId,
        int number,
        String title,
        String description,
        IssueStatus status,
        IssuePriority priority,
        Long assigneeId,
        String assigneeName,
        String reporterName,
        LocalDate dueDate,
        /** 삭제 가능 여부. 작성자이거나 프로젝트 관리자일 때만 참이다. */
        boolean canDelete,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static IssueResponse of(Issue issue, boolean canDelete) {
        return new IssueResponse(
                issue.getId(),
                issue.getProject().getId(),
                issue.getNumber(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getAssignee() != null ? issue.getAssignee().getId() : null,
                issue.getAssignee() != null ? issue.getAssignee().getName() : null,
                issue.getReporter().getName(),
                issue.getDueDate(),
                canDelete,
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }
}
