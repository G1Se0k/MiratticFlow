package com.mirattic.flow.chat.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_message_topic", columnList = "topic_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    /** 시스템 메시지는 보낸 사람이 없다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageType type;

    /**
     * SYSTEM 문구에 이름이 들어간 사람. 맨 앞 "{이름}님이 …"(lead)와 끝 "… {이름}님으로 지정했습니다."(assignee).
     * 그 사람이 탈퇴하면 그 자리의 이름만 "탈퇴한 사용자"로 바꾼다 (UserService.withdraw) — 문구 전체를 이름으로
     * 찾지 않으므로 같은 이름의 다른 사람 기록은 건드리지 않는다.
     */
    @Column(name = "lead_user_id")
    private Long leadUserId;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    private ChatMessage(Topic topic, User sender, String content, MessageType type) {
        this.topic = topic;
        this.sender = sender;
        this.content = content;
        this.type = type;
    }

    public static ChatMessage user(Topic topic, User sender, String content) {
        return new ChatMessage(topic, sender, content, MessageType.USER);
    }

    public static ChatMessage system(Topic topic, String content, Long leadUserId, Long assigneeUserId) {
        ChatMessage message = new ChatMessage(topic, null, content, MessageType.SYSTEM);
        message.leadUserId = leadUserId;
        message.assigneeUserId = assigneeUserId;
        return message;
    }
}
