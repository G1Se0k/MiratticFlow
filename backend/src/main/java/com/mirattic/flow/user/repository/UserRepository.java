package com.mirattic.flow.user.repository;

import com.mirattic.flow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Mirattic Auth 의 `sub` 로 찾는다. 이메일로는 찾지 않는다 (같은 이메일이 다른 계정일 수 있다). */
    Optional<User> findByMiratticUid(String miratticUid);

    /**
     * 아직 탈퇴하지 않았으면 그 행을 잠그고 [id], 이미 탈퇴했으면 []. 잠금 읽기라 방금 커밋된 탈퇴까지 본다
     * (트랜잭션 앞에서 읽어 둔 엔티티는 그 전 상태일 수 있다).
     */
    @Query(value = "select id from users where id = :id and deleted_at is null for update", nativeQuery = true)
    List<Long> lockIfNotWithdrawn(@Param("id") Long id);
}
