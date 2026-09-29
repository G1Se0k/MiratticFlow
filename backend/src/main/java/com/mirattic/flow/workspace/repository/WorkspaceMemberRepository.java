package com.mirattic.flow.workspace.repository;

import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 관리자 자리를 잠그고 읽는다 (SELECT ... FOR UPDATE). 관리자가 빠지는 변경(탈퇴 · 나가기 · 내보내기 · 역할 변경)은
     * 이걸로 센다: 잠금 읽기는 방금 커밋된 행까지 보므로, 마지막 관리자 둘이 동시에 빠져도 한쪽은 기다렸다가 한 명만 남은
     * 것을 보고 거절된다. 그냥 count 는 트랜잭션의 스냅샷을 읽어 둘 다 통과할 수 있다 (주인 없는 워크스페이스).
     */
    /**
     * 탈퇴할 사람의 멤버십 전부를 잠그고 읽는다. 잠금 읽기라 트랜잭션 스냅샷이 아니라 지금 역할을 본다 — 탈퇴 도중
     * 관리자로 승격되고 다른 관리자가 나간 경우를 놓치지 않는다. 승격은 이 행을 기다린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WorkspaceMember m join fetch m.workspace where m.user.id = :userId")
    List<WorkspaceMember> lockAllByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WorkspaceMember m where m.workspace.id = :workspaceId and m.role = :role")
    List<WorkspaceMember> lockAllByWorkspaceIdAndRole(@Param("workspaceId") Long workspaceId,
                                                      @Param("role") WorkspaceRole role);

    /** 탈퇴 시 "내가 유일한 관리자인 워크스페이스"를 찾기 위한 조회. */
    List<WorkspaceMember> findAllByUserIdAndRole(Long userId, WorkspaceRole role);

    @Modifying(clearAutomatically = true)
    @Query("delete from WorkspaceMember m where m.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from WorkspaceMember m where m.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
