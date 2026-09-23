package com.mirattic.flow.issue.dto;

import java.util.List;

/**
 * 프로젝트 대시보드가 한 번에 받는 값.
 *
 * 카드 네 개를 서버에서 계산해서 보낸다. byStatus 로도 구할 수 있지만,
 * 그러면 화면이 "IN_PROGRESS 가 진행 중" 같은 도메인 규칙을 알아야 한다.
 */
public record ProjectStatsResponse(
        long total,
        long inProgress,
        long done,
        /** 내가 담당자인 이슈 수. */
        long mine,
        List<StatusCount> byStatus,
        List<PriorityCount> byPriority,
        List<AssigneeCount> byAssignee,
        List<ActivityResponse> recentActivity) {
}
