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

    private ChatMessage(Topic topic, User sender, String content, MessageType type) {
        this.topic = topic;
        this.sender = sender;
        this.content = content;
        this.type = type;
    }

    public static ChatMessage user(Topic topic, User sender, String content) {
        return new ChatMessage(topic, sender, content, MessageType.USER);
    }

    public static ChatMessage system(Topic topic, String content) {
        return new ChatMessage(topic, null, content, MessageType.SYSTEM);
    }
}
