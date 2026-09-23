package com.mirattic.flow.issue.repository;

import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * 검색 + 필터 + 정렬을 한 쿼리로 처리한다.
     * 파라미터가 null 이면 그 조건은 건너뛴다 — 조건 조합마다 메서드를 만들면
     * 4개 필터에 16가지 메서드가 필요해진다. 정렬과 페이징은 Pageable 이 붙여준다.
     *
     * Querydsl 이나 Specification 을 쓸 수도 있지만, 조건이 넷뿐이고 더 늘어날 계획이 없어
     * 읽는 사람이 SQL 을 그대로 떠올릴 수 있는 JPQL 쪽을 골랐다.
     */
    @Query("""
            select i from Issue i
            join fetch i.reporter
            left join fetch i.assignee
            where i.project.id = :projectId
              and (:status is null or i.status = :status)
              and (:priority is null or i.priority = :priority)
              and (:assigneeId is null or i.assignee.id = :assigneeId)
              and (:keyword is null or lower(i.title) like lower(concat('%', :keyword, '%')))
            """)
    Page<Issue> search(@Param("projectId") Long projectId,
                       @Param("status") IssueStatus status,
                       @Param("priority") IssuePriority priority,
                       @Param("assigneeId") Long assigneeId,
                       @Param("keyword") String keyword,
                       Pageable pageable);

    @Query("select i from Issue i join fetch i.project join fetch i.reporter left join fetch i.assignee where i.id = :id")
    Optional<Issue> findByIdWithDetails(@Param("id") Long id);

    @Query("select coalesce(max(i.number), 0) from Issue i where i.project.id = :projectId")
    int findLastNumber(@Param("projectId") Long projectId);

    @Modifying
    @Query("delete from Issue i where i.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    /** 워크스페이스를 지울 때 한 번에 정리한다. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("delete from Issue i where i.project.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
