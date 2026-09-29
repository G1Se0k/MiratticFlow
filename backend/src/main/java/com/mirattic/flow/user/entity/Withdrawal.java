package com.mirattic.flow.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈퇴한 Mirattic 계정의 마지막 탈퇴 시각. 같은 계정이 다시 가입할 때, 탈퇴보다 뒤에 한 로그인이어야 새 사용자를
 * 만든다 — 다른 브라우저에 남아 있던 탈퇴 전의 Auth 로그인 세션으로 다시 가입하면, 그 로그인의 오래된 토큰들이
 * 새 사용자로 통하게 되기 때문이다 (User.enrolledAuthTime).
 * 개인정보를 남기지 않으려고 Mirattic UID 는 SHA-256 으로만 둔다.
 */
@Getter
@Entity
@Table(name = "withdrawals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Withdrawal {

    @Id
    @Column(length = 64)
    private String miratticUidHash;

    /** 마지막 탈퇴 시각 (epoch 초). */
    @Column(nullable = false)
    private long withdrawnAt;

    public Withdrawal(String miratticUidHash, long withdrawnAt) {
        this.miratticUidHash = miratticUidHash;
        this.withdrawnAt = withdrawnAt;
    }

    public void withdrawnAgain(long at) {
        this.withdrawnAt = at;
    }
}
