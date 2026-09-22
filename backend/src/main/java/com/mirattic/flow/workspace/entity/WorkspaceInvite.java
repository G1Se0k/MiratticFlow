package com.mirattic.flow.workspace.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 초대 링크와 초대 코드를 같은 테이블로 관리한다.
 * 참여 처리 로직(코드 조회 → 유효성 확인 → 멤버 추가)이 완전히 같고,
 * 다른 것은 수명 정책(만료·사용 횟수)뿐이라 테이블을 나누면 같은 코드를 두 번 쓰게 된다.
 */
@Getter
@Entity
@Table(name = "workspace_invites", indexes = @Index(name = "idx_invite_code", columnList = "code"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InviteType type;

    /** null 이면 만료 없음 (상시 코드) */
    private LocalDateTime expiresAt;

    /** null 이면 횟수 제한 없음 (상시 코드) */
    private Integer maxUses;

    @Column(nullable = false)
    private int usedCount;

    @Column(nullable = false)
    private boolean revoked;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private WorkspaceInvite(Workspace workspace, String code, InviteType type,
                            LocalDateTime expiresAt, Integer maxUses, User createdBy) {
        this.workspace = workspace;
        this.code = code;
        this.type = type;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.createdBy = createdBy;
    }

    /** 일회용 링크. 기본 7일. */
    public static WorkspaceInvite link(Workspace workspace, String code, User createdBy, int validDays) {
        return new WorkspaceInvite(workspace, code, InviteType.LINK,
                LocalDateTime.now().plusDays(validDays), 1, createdBy);
    }

    /** 워크스페이스 상시 코드. 만료도 횟수 제한도 없다. */
    public static WorkspaceInvite code(Workspace workspace, String code, User createdBy) {
        return new WorkspaceInvite(workspace, code, InviteType.CODE, null, null, createdBy);
    }

    public boolean isUsable() {
        if (revoked) return false;
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) return false;
        return maxUses == null || usedCount < maxUses;
    }

    public void use() {
        usedCount++;
    }

    public void revoke() {
        this.revoked = true;
    }
}
