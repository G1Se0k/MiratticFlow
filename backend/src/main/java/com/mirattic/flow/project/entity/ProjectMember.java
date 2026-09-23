package com.mirattic.flow.project.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 참여자. WorkspaceMember 와 달리 역할이 없다 —
 * 관리 권한은 Project.createdBy 와 워크스페이스 OWNER 로 결정된다.
 * 이슈 담당자 후보와 채팅방 입장 권한이 이 테이블을 기준으로 한다.
 */
@Getter
@Entity
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_member", columnNames = {"project_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private ProjectMember(Project project, User user) {
        this.project = project;
        this.user = user;
    }

    public static ProjectMember join(Project project, User user) {
        return new ProjectMember(project, user);
    }
}
