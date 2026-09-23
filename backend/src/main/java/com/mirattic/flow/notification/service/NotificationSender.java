package com.mirattic.flow.notification.service;

import com.mirattic.flow.notification.entity.Notification;
import com.mirattic.flow.notification.entity.NotificationType;
import com.mirattic.flow.notification.repository.NotificationRepository;
import com.mirattic.flow.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 다른 도메인이 알림을 만들 때 쓰는 입구.
 *
 * NotificationService 와 나눠 둔 이유는 SystemMessageSender 와 같다.
 * 이쪽은 서버가 스스로 만드는 알림이라 권한 검사가 필요 없고, 그래서 아무 서비스나 불러도
 * 순환 참조가 생기지 않는다. 읽기·읽음 처리는 "내 알림인가"를 따져야 하므로 서비스에 둔다.
 */
@Component
@RequiredArgsConstructor
public class NotificationSender {

    private final NotificationRepository notificationRepository;

    /**
     * 받는 사람을 여러 명 넘길 수 있다. 행위자 본인과 null(담당자 없음), 중복은 여기서 걸러낸다.
     * 부르는 쪽마다 같은 걸러내기를 반복하면 한 군데서 빠뜨리게 된다.
     */
    @Transactional
    public void send(Long actorId, NotificationType type, String content, String link, User... recipients) {
        Map<Long, User> targets = new LinkedHashMap<>();
        Arrays.stream(recipients)
                .filter(Objects::nonNull)
                .filter(user -> !user.getId().equals(actorId)) // 내가 한 일을 나에게 알리지 않는다
                .forEach(user -> targets.putIfAbsent(user.getId(), user));

        notificationRepository.saveAll(targets.values().stream()
                .map(user -> Notification.of(user, type, content, link))
                .toList());
    }
}
