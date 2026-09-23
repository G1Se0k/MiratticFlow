package com.mirattic.flow.issue.dto;

import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 생성 시 status 는 무시된다(항상 TODO). assigneeId 가 null 이면 담당자 없음. */
public record IssueRequest(
        @NotBlank(message = "제목을 입력해주세요.") @Size(max = 100) String title,
        @Size(max = 5000) String description,
        IssueStatus status,
        IssuePriority priority,
        Long assigneeId,
        LocalDate dueDate) {
}
