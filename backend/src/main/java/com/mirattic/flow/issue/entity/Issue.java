package com.mirattic.flow.issue.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "issues",
        uniqueConstraints = @UniqueConstraint(name = "uk_issue_number", columnNames = {"project_id", "number"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    /**
     * 프로젝트 안에서의 번호. 화면과 채팅 시스템 메시지에 "ISSUE-12" 로 쓴다.
     * 전역 PK 는 사용자에게 의미가 없고, 프로젝트마다 1번부터 세는 편이 읽기 쉽다.
     */
    @Column(nullable = false)
    private int number;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssuePriority priority;

    /** 담당자는 없을 수 있다. 일단 등록해두고 나중에 정하는 흐름이 흔하다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    private LocalDate dueDate;

    private Issue(Project project, int number, String title, String description,
                  IssuePriority priority, User assignee, User reporter, LocalDate dueDate) {
        this.project = project;
        this.number = number;
        this.title = title;
        this.description = description;
        this.status = IssueStatus.TODO;
        this.priority = priority;
        this.assignee = assignee;
        this.reporter = reporter;
        this.dueDate = dueDate;
    }

    public static Issue create(Project project, int number, String title, String description,
                               IssuePriority priority, User assignee, User reporter, LocalDate dueDate) {
        return new Issue(project, number, title, description, priority, assignee, reporter, dueDate);
    }

    public void update(String title, String description, IssueStatus status, IssuePriority priority,
                       User assignee, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.assignee = assignee;
        this.dueDate = dueDate;
    }

    public void changeStatus(IssueStatus status) {
        this.status = status;
    }

    public boolean isReportedBy(Long userId) {
        return reporter.getId().equals(userId);
    }
}
