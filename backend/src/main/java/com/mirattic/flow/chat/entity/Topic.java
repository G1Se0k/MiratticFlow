package com.mirattic.flow.chat.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 안의 대화 주제. 주제마다 메시지 스레드가 따로 쌓인다.
 * 프로젝트를 만들면 "일반" 주제가 하나 자동으로 생긴다.
 */
@Getter
@Entity
@Table(name = "topics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private Topic(Project project, String name, String description, User createdBy) {
        this.project = project;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    public static Topic create(Project project, String name, String description, User createdBy) {
        return new Topic(project, name, description, createdBy);
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public boolean isCreatedBy(Long userId) {
        return createdBy.getId().equals(userId);
    }
}
