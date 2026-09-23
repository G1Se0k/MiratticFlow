package com.mirattic.flow.chat.dto;

import com.mirattic.flow.chat.entity.Topic;

import java.time.LocalDateTime;

public record TopicResponse(
        Long id,
        Long projectId,
        /** null 이면 프로젝트 채팅. */
        Long issueId,
        String name,
        String description,
        String createdByName,
        /** 이름 변경·삭제 가능 여부. 만든 사람이거나 프로젝트 관리자일 때 참이다. */
        boolean canManage,
        LocalDateTime createdAt) {

    public static TopicResponse of(Topic topic, boolean canManage) {
        return new TopicResponse(
                topic.getId(),
                topic.getProject().getId(),
                topic.isProjectChat() ? null : topic.getIssue().getId(),
                topic.getName(),
                topic.getDescription(),
                topic.getCreatedBy().getName(),
                canManage,
                topic.getCreatedAt());
    }
}
