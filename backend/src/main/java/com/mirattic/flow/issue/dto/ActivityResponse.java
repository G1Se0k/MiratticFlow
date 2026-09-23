package com.mirattic.flow.issue.dto;

import java.time.LocalDateTime;

/**
 * 최근 활동 한 줄.
 *
 * 채팅에 쌓이는 SYSTEM 메시지가 그대로 활동 기록이라 그걸 읽는다.
 * ChatMessageResponse 를 그대로 쓰지 않는 이유는 topicId·senderId·type 이
 * 대시보드와 상관없는 값이기 때문이다 — 채팅의 사정이 통계 API 로 새지 않게 한다.
 */
public record ActivityResponse(String content, LocalDateTime createdAt) {
}
