package com.mirattic.flow.auth.entity;

import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발급된 Refresh Token 을 서버가 들고 있는 이유:
 * JWT 는 그 자체로 검증되므로 서버가 아무것도 저장하지 않으면 만료 전까지 회수할 방법이 없다.
 * 로그아웃(=토큰 무효화)과 재사용 탐지를 하려면 "지금 유효한 refresh 목록"이 필요하다.
 */
@Getter
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_token", columnList = "token"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private RefreshToken(String token, User user, LocalDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(String token, User user, LocalDateTime expiresAt) {
        return new RefreshToken(token, user, expiresAt);
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
