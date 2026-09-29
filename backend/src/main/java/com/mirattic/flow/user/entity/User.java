package com.mirattic.flow.user.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Flow 의 사용자. 로그인 수단(이메일 · 비밀번호 · 카카오 · 네이버)은 Mirattic Auth 가 갖고,
 * 여기에는 Mirattic UID(miratticUid)와 Flow 에서 쓰는 정보만 둔다.
 *
 * id 는 Flow 내부 숫자 키로 그대로 둔다. 이슈 · 댓글 · 채팅이 모두 이 키를 참조하므로
 * UUID 로 바꾸면 모든 테이블의 외래키가 바뀐다. Auth 와의 연결은 miratticUid 한 칸으로 충분하다.
 */
@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_mirattic_uid", columnNames = "mirattic_uid"))
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용. 외부에서는 create() 로만 만든다.
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mirattic Auth 의 `sub`. 탈퇴하면 비워서 같은 계정으로 다시 오면 새 Flow 사용자가 된다. */
    @Column(length = 36)
    private String miratticUid;

    /**
     * 참여자 목록에 보여주는 연락처 사본. 로그인할 때마다 Auth 의 값으로 갱신한다.
     * 로그인이나 사용자 식별에는 쓰지 않으므로 유니크가 아니고, 없을 수도 있다 (카카오 이메일은 선택 동의).
     */
    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 이 Flow 사용자를 만든 로그인의 시각 (Auth 의 auth_time, epoch 초). 이보다 오래된 로그인의 토큰은 받지 않는다 —
     * 탈퇴한 뒤 같은 계정으로 다시 오면 새 사용자가 되는데, 탈퇴 전의 로그인(다른 브라우저의 refresh token 등)이
     * 새 사용자로 통하면 안 된다.
     */
    @Column(nullable = false)
    private long enrolledAuthTime;

    /**
     * 낙관적 잠금. 로그인(이메일 사본 갱신)과 탈퇴가 같은 행을 동시에 고치면, 먼저 읽고 늦게 쓰는 쪽이 실패한다 —
     * 탈퇴 전에 읽은 로그인이 탈퇴 뒤에 옛 값(Mirattic UID 등)을 되써서 탈퇴를 되돌리지 못하게.
     */
    @Version
    private long version;

    /** 탈퇴 시각. null 이면 정상 회원이다. */
    private LocalDateTime deletedAt;

    private User(String miratticUid, String email, String name, long enrolledAuthTime) {
        this.miratticUid = miratticUid;
        this.email = email;
        this.name = name;
        this.enrolledAuthTime = enrolledAuthTime;
    }

    /**
     * Mirattic 계정으로 처음 들어온 사용자. 이름은 Auth 의 이름으로 시작하고 이후 Flow 에서 바꿀 수 있다.
     * enrolledAuthTime: 그 로그인의 auth_time (epoch 초).
     */
    public static User create(String miratticUid, String email, String name, long enrolledAuthTime) {
        return new User(miratticUid, email, name, enrolledAuthTime);
    }

    /** 이 토큰(의 로그인)이 이 사용자가 생긴 뒤의 것인가. */
    public boolean acceptsLoginAt(long authTime) {
        return authTime >= enrolledAuthTime;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    /**
     * 탈퇴. 행을 지우지 않고 식별 정보만 비운다.
     *
     * 이 행을 참조하는 이슈 · 댓글 · 채팅이 남아 있어 삭제할 수 없다.
     * 대신 개인정보 컬럼을 실제로 비우므로 "보관 중인데 가려둔" 상태가 아니라 파기한 상태다.
     * miratticUid 도 비우므로 같은 Mirattic 계정으로 다시 로그인하면 새 사용자로 시작한다.
     */
    public void withdraw() {
        this.miratticUid = null;
        this.email = null;
        this.name = "탈퇴한 사용자";
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }
}
