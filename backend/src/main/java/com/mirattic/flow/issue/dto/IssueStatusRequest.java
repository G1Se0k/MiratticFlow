package com.mirattic.flow.issue.dto;

import com.mirattic.flow.issue.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;

/** 목록에서 상태만 빠르게 바꾸는 용도. 수정 API 는 제목까지 전부 보내야 한다. */
public record IssueStatusRequest(@NotNull IssueStatus status) {
}
