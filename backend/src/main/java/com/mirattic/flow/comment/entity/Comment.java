package com.mirattic.flow.comment.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Comment(Issue issue, User author, String content) {
        this.issue = issue;
        this.author = author;
        this.content = content;
    }

    public static Comment write(Issue issue, User author, String content) {
        return new Comment(issue, author, content);
    }

    public void edit(String content) {
        this.content = content;
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }
}
