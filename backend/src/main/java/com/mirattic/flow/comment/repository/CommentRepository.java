package com.mirattic.flow.comment.repository;

import com.mirattic.flow.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 작성자를 함께 읽는다. 댓글 수만큼 쿼리가 나가는 것을 막는다. */
    @Query("select c from Comment c join fetch c.author where c.issue.id = :issueId order by c.id")
    List<Comment> findAllByIssueIdWithAuthor(@Param("issueId") Long issueId);

    @Query("select c from Comment c join fetch c.issue join fetch c.author where c.id = :id")
    Optional<Comment> findByIdWithDetails(@Param("id") Long id);

    void deleteByIssueId(Long issueId);

    // 상위 엔티티를 지울 때 한 번에 정리한다. 단건 삭제를 반복하면 댓글 수만큼 delete 가 나간다.
    @Modifying(clearAutomatically = true)
    @Query("delete from Comment c where c.issue.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Comment c where c.issue.project.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
