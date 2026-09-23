package com.mirattic.flow.chat.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메시지 스레드가 쌓이는 단위. 두 가지로 쓰인다.
 *
 * - issue 가 null 이면 프로젝트 채팅. 프로젝트를 만들 때 하나만 생기고 지울 수 없다.
 * - issue 가 있으면 그 이슈 안에서 만든 주제. 이슈마다 여러 개 만들 수 있다.
 *
 * 이슈 주제도 project 를 같이 들고 있다. 권한 검사와 프로젝트 삭제 시 연쇄 삭제가
 * 모두 project 기준이라, 여기서 한 번 채워 두면 그 질의들을 그대로 쓸 수 있다.
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

    /** null 이면 프로젝트 채팅. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private Topic(Project project, Issue issue, String name, String description, User createdBy) {
        this.project = project;
        this.issue = issue;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    /** 프로젝트를 만들 때 딱 한 번 생기는 채팅방. */
    public static Topic projectChat(Project project, User createdBy) {
        return new Topic(project, null, "프로젝트 채팅", "프로젝트 전반에 대한 이야기", createdBy);
    }

    public static Topic forIssue(Issue issue, String name, String description, User createdBy) {
        return new Topic(issue.getProject(), issue, name, description, createdBy);
    }

    public boolean isProjectChat() {
        return issue == null;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public boolean isCreatedBy(Long userId) {
        return createdBy.getId().equals(userId);
    }
}
