package com.mirattic.flow.chat.repository;

import com.mirattic.flow.chat.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    /**
     * 프로젝트 채팅. 시스템 메시지도 여기로 간다.
     * 프로젝트를 만들 때 하나만 생기지만, 조회가 데이터 상태 때문에 터지지 않도록
     * 가장 먼저 만들어진 것 하나로 못박는다.
     */
    @Query("select t from Topic t join fetch t.createdBy where t.project.id = :projectId and t.issue is null order by t.id limit 1")
    Optional<Topic> findProjectChat(@Param("projectId") Long projectId);

    @Query("select t from Topic t join fetch t.createdBy where t.issue.id = :issueId order by t.id")
    List<Topic> findAllByIssueId(@Param("issueId") Long issueId);

    @Query("select t from Topic t join fetch t.project where t.id = :id")
    Optional<Topic> findByIdWithProject(@Param("id") Long id);

    @Modifying
    @Query("delete from Topic t where t.issue.id = :issueId")
    void deleteByIssueId(@Param("issueId") Long issueId);

    @Modifying
    @Query("delete from Topic t where t.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Topic t where t.project.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
