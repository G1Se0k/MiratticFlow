package com.mirattic.flow.user.service;

import com.mirattic.flow.support.ApiTestSupport;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 로그인과 탈퇴가 같은 사용자 행을 동시에 고칠 때 (User.version). */
class UserConcurrencyTest extends ApiTestSupport {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired TransactionTemplate tx;

    @Test
    @DisplayName("탈퇴 전에 읽은 로그인이 탈퇴 뒤에 옛 값을 쓰면 실패한다 — 탈퇴가 되돌려지지 않는다")
    void aStaleSignInCannotUndoAWithdrawal() {
        String uid = UUID.randomUUID().toString();
        Long id = userService.signIn(uid, "old@test.com", "경합", Instant.now());

        // 로그인 쪽 트랜잭션이 탈퇴 전에 읽어 둔 사용자
        User stale = userRepository.findById(id).orElseThrow();
        userService.withdraw(id);

        stale.updateEmail("new@test.com");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> userRepository.saveAndFlush(stale)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        User now = userRepository.findById(id).orElseThrow();
        assertThat(now.isWithdrawn()).isTrue();
        assertThat(now.getMiratticUid()).isNull();
    }
}
