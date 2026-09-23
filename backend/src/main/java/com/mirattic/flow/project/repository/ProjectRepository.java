package com.mirattic.flow.project.repository;

import com.mirattic.flow.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** 워크스페이스 OWNER 용 — 워크스페이스의 모든 프로젝트. createdBy 는 응답에 쓰이므로 같이 읽는다. */
    @Query("select p from Project p join fetch p.createdBy where p.workspace.id = :workspaceId order by p.id desc")
    List<Project> findAllByWorkspaceIdWithCreator(@Param("workspaceId") Long workspaceId);

    /** 일반 멤버용 — 내가 참여한 프로젝트만. */
    @Query("""
            select p from Project p join fetch p.createdBy
            where p.workspace.id = :workspaceId
              and exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId)
            order by p.id desc""")
    List<Project> findMineByWorkspaceId(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** 상세 조회. workspace 와 createdBy 를 함께 읽어 트랜잭션 밖 지연 로딩을 피한다. */
    @Query("select p from Project p join fetch p.workspace join fetch p.createdBy where p.id = :id")
    java.util.Optional<Project> findByIdWithDetails(@Param("id") Long id);

    @Modifying
    @Query("delete from Project p where p.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
