package com.mirattic.flow.issue.dto;

import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 목록용. 본문(description)은 빼고 한 줄에 보일 것만 담는다. */
public record IssueSummaryResponse(
        Long id,
        int number,
        String title,
        IssueStatus status,
        IssuePriority priority,
        Long assigneeId,
        String assigneeName,
        LocalDate dueDate,
        LocalDateTime createdAt) {

    public static IssueSummaryResponse from(Issue issue) {
        return new IssueSummaryResponse(
                issue.getId(),
                issue.getNumber(),
                issue.getTitle(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getAssignee() != null ? issue.getAssignee().getId() : null,
                issue.getAssignee() != null ? issue.getAssignee().getName() : null,
                issue.getDueDate(),
                issue.getCreatedAt());
    }
}
