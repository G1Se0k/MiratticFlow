package com.mirattic.flow.user.repository;

import com.mirattic.flow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Mirattic Auth 의 `sub` 로 찾는다. 이메일로는 찾지 않는다 (같은 이메일이 다른 계정일 수 있다). */
    Optional<User> findByMiratticUid(String miratticUid);
}
