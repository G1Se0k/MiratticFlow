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

    /** 담당자 지정 문구의 끝: "… {이름}" + ASSIGNED_TAIL. */
    public static final String ASSIGNED_TAIL = "님으로 지정했습니다.";

    /**
     * SYSTEM 문구에 이름이 들어간 사람과 그 이름의 길이(글자 수). 맨 앞 "{이름}님이 …"(lead)와 끝
     * "… {이름}님으로 지정했습니다."(assignee). 그 사람이 탈퇴하면 그 자리만 "탈퇴한 사용자"로 바꾼다
     * (UserService.withdraw) — 이름으로 찾지 않으므로 그사이 이름을 바꿨어도, 같은 이름의 다른 사람이 있어도 정확하다.
     */
    @Column(name = "lead_user_id")
    private Long leadUserId;

    @Column(name = "lead_name_length")
    private Integer leadNameLength;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "assignee_name_length")
    private Integer assigneeNameLength;

    private ChatMessage(Topic topic, User sender, String content, MessageType type) {
        this.topic = topic;
        this.sender = sender;
        this.content = content;
        this.type = type;
    }

    public static ChatMessage user(Topic topic, User sender, String content) {
        return new ChatMessage(topic, sender, content, MessageType.USER);
    }

    /** lead: 문구 맨 앞에 이름이 오는 사람, assignee: 끝에 오는 사람 (없으면 null). */
    public static ChatMessage system(Topic topic, String content, User lead, User assignee) {
        ChatMessage message = new ChatMessage(topic, null, content, MessageType.SYSTEM);
        if (lead != null) {
            message.leadUserId = lead.getId();
            message.leadNameLength = chars(lead.getName());
        }
        if (assignee != null) {
            message.assigneeUserId = assignee.getId();
            message.assigneeNameLength = chars(assignee.getName());
        }
        return message;
    }

    /** MySQL CHAR_LENGTH 와 같은 글자 수 (이모지 같은 보조 문자도 한 글자). */
    public static int chars(String s) {
        return s.codePointCount(0, s.length());
    }
}
