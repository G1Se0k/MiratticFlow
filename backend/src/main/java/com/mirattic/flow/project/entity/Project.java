package com.mirattic.flow.project.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.workspace.entity.Workspace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    /**
     * 만든 사람. 화면에 표시하기도 하고, 수정·삭제 권한의 기준이기도 하다.
     * 이 필드 하나로 충분해서 ProjectMember 에는 역할 구분을 두지 않았다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private Project(Workspace workspace, String name, String description, User createdBy) {
        this.workspace = workspace;
        this.name = name;
        this.description = description;
        this.status = ProjectStatus.ACTIVE;
        this.createdBy = createdBy;
    }

    public static Project create(Workspace workspace, String name, String description, User createdBy) {
        return new Project(workspace, name, description, createdBy);
    }

    public void update(String name, String description, ProjectStatus status) {
        this.name = name;
        this.description = description;
        this.status = status;
    }

    public boolean isCreatedBy(Long userId) {
        return createdBy.getId().equals(userId);
    }
}
