package com.mirattic.flow.user.repository;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Mirattic Auth 의 `sub` 로 찾는다. 이메일로는 찾지 않는다 (같은 이메일이 다른 계정일 수 있다). */
    Optional<User> findByMiratticUid(String miratticUid);

    /**
     * 아직 탈퇴하지 않았으면 그 행을 잠그고 [id], 이미 탈퇴했으면 []. 잠금 읽기라 방금 커밋된 탈퇴까지 본다
     * (트랜잭션 앞에서 읽어 둔 엔티티는 그 전 상태일 수 있다).
     */
    @Query(value = "select id from users where id = :id and deleted_at is null for update", nativeQuery = true)
    List<Long> lockIfNotWithdrawn(@Param("id") Long id);

    @Query(value = "select id from users where id in (:ids) and deleted_at is null order by id for update",
            nativeQuery = true)
    List<Long> lockActive(@Param("ids") Collection<Long> ids);

    /**
     * 이름이 들어가는 기록 문구(채팅 활동 줄, 댓글 알림)를 쓰는 트랜잭션의 첫 문장: 그 사람들의 행을 id 순으로 잠근다.
     * 탈퇴(UserService.withdraw)도 그 행을 잠그므로 둘은 차례로 — 탈퇴가 끝난 뒤에 예전 이름이 새로 쓰이지 않고, 먼저
     * 쓰인 문구는 탈퇴가 바꾼다. 잠금 읽기 뒤의 첫 일반 읽기에서 스냅샷이 잡혀 읽는 이름도 지금 것이다. 다른 쓰기보다
     * 먼저 잡아서 탈퇴의 일괄 변경과 서로 막히지 않는다. 탈퇴한 사람이 있으면 USER_NOT_FOUND. null 은 건너뛴다.
     */
    default void lockNamed(Long... ids) {
        Set<Long> named = Arrays.stream(ids).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!named.isEmpty() && lockActive(named).size() != named.size()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
