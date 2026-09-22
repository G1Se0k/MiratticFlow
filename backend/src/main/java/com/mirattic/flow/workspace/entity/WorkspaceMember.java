package com.mirattic.flow.workspace.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * User 와 Workspace 의 다대다를 푼 중간 엔티티.
 * role 이라는 고유 속성이 있으므로 @ManyToMany 가 아니라 별도 엔티티여야 한다.
 */
@Getter
@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_workspace_member", columnNames = {"workspace_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    private WorkspaceMember(Workspace workspace, User user, WorkspaceRole role) {
        this.workspace = workspace;
        this.user = user;
        this.role = role;
    }

    public static WorkspaceMember join(Workspace workspace, User user, WorkspaceRole role) {
        return new WorkspaceMember(workspace, user, role);
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }

    public boolean isOwner() {
        return role == WorkspaceRole.OWNER;
    }
}
