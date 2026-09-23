package com.mirattic.flow.workspace.repository;

import com.mirattic.flow.workspace.entity.InviteType;
import com.mirattic.flow.workspace.entity.WorkspaceInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {

    /** 참여 처리용. workspace 를 함께 읽는다. */
    @Query("select i from WorkspaceInvite i join fetch i.workspace where i.code = :code")
    Optional<WorkspaceInvite> findByCodeWithWorkspace(@Param("code") String code);

    Optional<WorkspaceInvite> findByWorkspaceIdAndTypeAndRevokedFalse(Long workspaceId, InviteType type);

    List<WorkspaceInvite> findAllByWorkspaceIdAndTypeAndRevokedFalseOrderByIdDesc(Long workspaceId, InviteType type);

    boolean existsByCode(String code);

    @Modifying
    @Query("delete from WorkspaceInvite i where i.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
