package com.mirattic.flow.project.repository;

import com.mirattic.flow.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    @Query("select m from ProjectMember m join fetch m.user where m.project.id = :projectId order by m.id")
    List<ProjectMember> findAllByProjectIdWithUser(@Param("projectId") Long projectId);

    /**
     * 목록 화면의 참여자 수.
     * 프로젝트마다 count 를 날리면 N+1 이 되므로 id 를 한꺼번에 넘겨 group by 한 번으로 끝낸다.
     */
    @Query("select m.project.id, count(m) from ProjectMember m where m.project.id in :projectIds group by m.project.id")
    List<Object[]> countByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from ProjectMember m where m.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from ProjectMember m where m.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("delete from ProjectMember m where m.project.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
