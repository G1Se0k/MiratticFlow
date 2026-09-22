package com.mirattic.flow.workspace.repository;

import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    /** 내 워크스페이스 목록. workspace 를 함께 읽어 목록 길이만큼 쿼리가 나가는 것(N+1)을 막는다. */
    @Query("select m from WorkspaceMember m join fetch m.workspace where m.user.id = :userId order by m.id desc")
    List<WorkspaceMember> findAllByUserIdWithWorkspace(@Param("userId") Long userId);

    /** 멤버 목록. user 를 함께 읽는다. */
    @Query("select m from WorkspaceMember m join fetch m.user where m.workspace.id = :workspaceId order by m.id")
    List<WorkspaceMember> findAllByWorkspaceIdWithUser(@Param("workspaceId") Long workspaceId);

    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);

    void deleteByWorkspaceId(Long workspaceId);
}
