package com.mirattic.flow.workspace.service;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.project.repository.ProjectRepository;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.workspace.dto.MemberResponse;
import com.mirattic.flow.workspace.dto.WorkspaceRequest;
import com.mirattic.flow.workspace.dto.WorkspaceResponse;
import com.mirattic.flow.workspace.entity.Workspace;
import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import com.mirattic.flow.workspace.repository.WorkspaceInviteRepository;
import com.mirattic.flow.workspace.repository.WorkspaceMemberRepository;
import com.mirattic.flow.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator codeGenerator;
    // 워크스페이스를 지우면 그 안의 프로젝트도 함께 사라져야 한다.
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueRepository issueRepository;

    // ---------------------------------------------------------------- 권한 검사
    // 워크스페이스에 속한 모든 기능이 이 두 메서드를 거친다.
    // 컨트롤러마다 검사를 흩어놓으면 하나 빠뜨리는 순간 구멍이 나므로 한 곳으로 모은다.
    // Phase 4(프로젝트)·5(이슈)도 여기를 호출한다.

    public WorkspaceMember requireMember(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                // 멤버가 아니면 워크스페이스의 존재 여부조차 알려주지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER));
    }

    public WorkspaceMember requireOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMember(workspaceId, userId);
        if (!member.isOwner()) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_OWNER);
        }
        return member;
    }

    /** 다른 도메인(프로젝트 등)이 예외 없이 소속·권한만 확인할 때 쓴다. */
    public boolean isMember(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent();
    }

    public boolean isOwner(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(WorkspaceMember::isOwner)
                .isPresent();
    }

    // ---------------------------------------------------------------- 워크스페이스

    @Transactional
    public WorkspaceResponse create(Long userId, WorkspaceRequest request) {
        User user = findUser(userId);
        Workspace workspace = workspaceRepository.save(Workspace.create(request.name(), request.description()));

        // 만든 사람이 곧 관리자다.
        memberRepository.save(WorkspaceMember.join(workspace, user, WorkspaceRole.OWNER));
        // 상시 참여 코드는 워크스페이스 생성과 함께 하나 만들어 둔다.
        inviteRepository.save(com.mirattic.flow.workspace.entity.WorkspaceInvite.code(
                workspace, generateUniqueJoinCode(), user));

        return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER);
    }

    public List<WorkspaceResponse> findMine(Long userId) {
        return memberRepository.findAllByUserIdWithWorkspace(userId).stream()
                .map(member -> WorkspaceResponse.of(member.getWorkspace(), member.getRole()))
                .toList();
    }

    public WorkspaceResponse findOne(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMember(workspaceId, userId);
        return WorkspaceResponse.of(member.getWorkspace(), member.getRole());
    }

    @Transactional
    public WorkspaceResponse update(Long workspaceId, Long userId, WorkspaceRequest request) {
        WorkspaceMember member = requireOwner(workspaceId, userId);
        member.getWorkspace().update(request.name(), request.description());
        return WorkspaceResponse.of(member.getWorkspace(), member.getRole());
    }

    @Transactional
    public void delete(Long workspaceId, Long userId) {
        requireOwner(workspaceId, userId);
        // 자식부터 지운다. FK 제약에 걸리지 않도록 순서가 중요하다.
        issueRepository.deleteByWorkspaceId(workspaceId);
        projectMemberRepository.deleteByWorkspaceId(workspaceId);
        projectRepository.deleteByWorkspaceId(workspaceId);
        inviteRepository.deleteByWorkspaceId(workspaceId);
        memberRepository.deleteByWorkspaceId(workspaceId);
        workspaceRepository.deleteById(workspaceId);
    }

    // ---------------------------------------------------------------- 멤버

    public List<MemberResponse> findMembers(Long workspaceId, Long userId) {
        requireMember(workspaceId, userId);
        return memberRepository.findAllByWorkspaceIdWithUser(workspaceId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(Long workspaceId, Long actorId, Long targetUserId, WorkspaceRole role) {
        requireOwner(workspaceId, actorId);
        // 실수로 자기 관리자 권한을 내려놓아 아무도 관리할 수 없게 되는 상황을 막는다.
        if (actorId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        WorkspaceMember target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (target.isOwner() && role != WorkspaceRole.OWNER) {
            requireAnotherOwnerExists(workspaceId);
        }
        target.changeRole(role);
        return MemberResponse.from(target);
    }

    @Transactional
    public void removeMember(Long workspaceId, Long actorId, Long targetUserId) {
        requireOwner(workspaceId, actorId);
        WorkspaceMember target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (target.isOwner()) {
            requireAnotherOwnerExists(workspaceId);
        }
        memberRepository.delete(target);
    }

    @Transactional
    public void leave(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMember(workspaceId, userId);
        if (member.isOwner()) {
            requireAnotherOwnerExists(workspaceId);
        }
        memberRepository.delete(member);
    }

    /** 관리자가 한 명뿐이면 그 자리를 비울 수 없다. 주인 없는 워크스페이스는 아무도 손댈 수 없다. */
    private void requireAnotherOwnerExists(Long workspaceId) {
        if (memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new BusinessException(ErrorCode.LAST_OWNER);
        }
    }

    // ---------------------------------------------------------------- 공용

    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    String generateUniqueJoinCode() {
        String code;
        do {
            code = codeGenerator.joinCode();
        } while (inviteRepository.existsByCode(code)); // 8자라 충돌 확률은 낮지만 0은 아니다.
        return code;
    }
}
