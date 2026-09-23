package com.mirattic.flow.notification.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 사람에게 간 알림 하나.
 *
 * content 에 완성된 문장을 그대로 담는다. type 과 참조 id 만 두고 읽을 때 조립하는 방법도 있지만,
 * 그러면 목록 한 번에 이슈·댓글을 다시 조회해야 하고(N+1), 이슈 제목이 나중에 바뀌면
 * 과거 알림 문장까지 소급해서 바뀐다. 알림은 그 시점의 사건 기록이라 그때 문장을 굳힌다.
 */
@Getter
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notification_user", columnList = "user_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String content;

    /** 누르면 갈 곳. 예: "/issues/12" */
    @Column(nullable = false, length = 200)
    private String link;

    // read 는 MySQL 예약어라 컬럼 이름을 따로 준다.
    @Column(name = "is_read", nullable = false)
    private boolean read;

    private Notification(User user, NotificationType type, String content, String link) {
        this.user = user;
        this.type = type;
        this.content = content;
        this.link = link;
    }

    public static Notification of(User user, NotificationType type, String content, String link) {
        return new Notification(user, type, content, link);
    }

}
