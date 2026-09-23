package com.mirattic.flow.chat.dto;

import com.mirattic.flow.chat.entity.ChatMessage;
import com.mirattic.flow.chat.entity.MessageType;

import java.time.LocalDateTime;

/** WebSocket 으로 브로드캐스트되는 모양과 REST 로 조회하는 모양이 같다. 화면이 둘을 구분할 필요가 없다. */
public record ChatMessageResponse(
        Long id,
        Long topicId,
        Long senderId,
        String senderName,
        String content,
        MessageType type,
        LocalDateTime createdAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getTopic().getId(),
                message.getSender() != null ? message.getSender().getId() : null,
                message.getSender() != null ? message.getSender().getName() : null,
                message.getContent(),
                message.getType(),
                message.getCreatedAt());
    }
}
