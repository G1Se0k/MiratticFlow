package com.mirattic.flow.notification.repository;

import com.mirattic.flow.notification.entity.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.user.id = :userId order by n.id desc")
    List<Notification> findRecent(@Param("userId") Long userId, Limit limit);

    long countByUserIdAndReadFalse(Long userId);

    /**
     * 읽음 처리는 받는 사람으로 범위를 좁힌 update 하나로 끝낸다.
     * 남의 알림 id 를 넣으면 0건이 바뀌므로 "없다"와 "내 것이 아니다"를 구분해 알려줄 일도 없다
     * — 존재 여부가 새지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.id = :id and n.user.id = :userId")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /** 목록을 다시 받지 않고 한 번에 처리한다. 건건이 엔티티를 불러올 이유가 없다. */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Notification n where n.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
