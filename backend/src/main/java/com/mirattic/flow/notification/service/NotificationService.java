package com.mirattic.flow.notification.service;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.notification.dto.NotificationResponse;
import com.mirattic.flow.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** 알림은 무한 스크롤을 붙이지 않는다. 최근 것만 보면 되는 정보라 개수를 고정한다. */
    private static final int RECENT_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public List<NotificationResponse> findRecent(Long userId) {
        return notificationRepository.findRecent(userId, Limit.of(RECENT_SIZE)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        if (notificationRepository.markRead(notificationId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
