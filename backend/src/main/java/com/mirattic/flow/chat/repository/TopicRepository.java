package com.mirattic.flow.chat.repository;

import com.mirattic.flow.chat.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query("select t from Topic t join fetch t.createdBy where t.project.id = :projectId order by t.id")
    List<Topic> findAllByProjectIdWithCreator(@Param("projectId") Long projectId);

    @Query("select t from Topic t join fetch t.project where t.id = :id")
    Optional<Topic> findByIdWithProject(@Param("id") Long id);

    /** 시스템 메시지가 갈 곳 — 프로젝트에서 가장 먼저 만들어진 주제("일반"). */
    @Query("select t from Topic t where t.project.id = :projectId order by t.id limit 1")
    Optional<Topic> findFirstByProjectId(@Param("projectId") Long projectId);

    long countByProjectId(Long projectId);

    @Modifying
    @Query("delete from Topic t where t.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Topic t where t.project.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
