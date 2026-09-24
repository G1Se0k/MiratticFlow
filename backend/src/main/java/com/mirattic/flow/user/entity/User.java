package com.mirattic.flow.user.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                // 같은 소셜 계정으로 두 번 가입되지 않도록 DB 레벨에서 막는다.
                @UniqueConstraint(name = "uk_users_provider", columnNames = {"provider", "provider_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용. 외부에서는 create() 로만 만든다.
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소셜 로그인은 이메일을 못 받을 수 있어 nullable 이다.
     * (카카오의 이메일은 선택 동의 항목이라 사용자가 거부하면 내려오지 않는다)
     */
    @Column(length = 100)
    private String email;

    /** BCrypt 해시. 소셜 가입자는 비밀번호가 없으므로 null 이다. */
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** 소셜 서비스가 부여한 고유 ID. LOCAL 사용자는 null. */
    @Column(length = 100)
    private String providerId;

    /** 탈퇴 시각. null 이면 정상 회원이다. */
    private LocalDateTime deletedAt;

    private User(String email, String password, String name, AuthProvider provider, String providerId) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static User create(String email, String encodedPassword, String name) {
        return new User(email, encodedPassword, name, AuthProvider.LOCAL, null);
    }

    public static User createSocial(AuthProvider provider, String providerId, String email, String name) {
        return new User(email, null, name, provider, providerId);
    }

    /** 소셜 가입자는 비밀번호가 없어 비밀번호 로그인을 할 수 없다. */
    public boolean hasPassword() {
        return password != null;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 탈퇴. 행을 지우지 않고 식별 정보만 비운다.
     *
     * 이 행을 참조하는 이슈 · 댓글 · 채팅이 남아 있어 삭제할 수 없다.
     * 대신 개인정보 컬럼을 실제로 비우므로 "보관 중인데 가려둔" 상태가 아니라 파기한 상태다.
     * email 과 providerId 가 null 이 되면서 로그인 조회에도 걸리지 않고,
     * 유니크 제약은 null 을 여럿 허용하므로 같은 이메일로 재가입할 수 있다.
     */
    public void withdraw() {
        this.email = null;
        this.password = null;
        this.providerId = null;
        this.name = "탈퇴한 사용자";
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }
}
