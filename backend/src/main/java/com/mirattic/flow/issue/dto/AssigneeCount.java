package com.mirattic.flow.issue.dto;

/** userId 가 null 이면 "담당자 없음". 아무도 안 맡은 이슈 수가 대시보드에서 제일 쓸모 있다. */
public record AssigneeCount(Long userId, String name, long count) {
}
