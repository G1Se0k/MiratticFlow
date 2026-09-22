package com.mirattic.flow.workspace.service;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.workspace.dto.InvitePreviewResponse;
import com.mirattic.flow.workspace.dto.InviteResponse;
import com.mirattic.flow.workspace.dto.JoinResponse;
import com.mirattic.flow.workspace.entity.*;
import com.mirattic.flow.workspace.repository.WorkspaceInviteRepository;
import com.mirattic.flow.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceInviteService {

    private static final int LINK_VALID_DAYS = 7;

    private final WorkspaceInviteRepository inviteRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceService workspaceService;
    private final InviteCodeGenerator codeGenerator;

    // ---------------------------------------------------------------- 초대 링크 (일회용)

    @Transactional
    public InviteResponse createLink(Long workspaceId, Long userId) {
        workspaceService.requireOwner(workspaceId, userId);
        Workspace workspace = workspaceService.findWorkspace(workspaceId);
        User creator = workspaceService.findUser(userId);

        String code;
        do {
            code = codeGenerator.linkCode();
        } while (inviteRepository.existsByCode(code));

        return InviteResponse.from(
                inviteRepository.save(WorkspaceInvite.link(workspace, code, creator, LINK_VALID_DAYS)));
    }

    public List<InviteResponse> findLinks(Long workspaceId, Long userId) {
        workspaceService.requireOwner(workspaceId, userId);
        return inviteRepository
                .findAllByWorkspaceIdAndTypeAndRevokedFalseOrderByIdDesc(workspaceId, InviteType.LINK).stream()
                .map(InviteResponse::from)
                .toList();
    }

    @Transactional
    public void revokeLink(Long inviteId, Long userId) {
        WorkspaceInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_INVALID));
        workspaceService.requireOwner(invite.getWorkspace().getId(), userId);
        invite.revoke();
    }

    // ---------------------------------------------------------------- 상시 코드

    /**
     * 상시 코드는 워크스페이스 생성과 함께 만들어지지만, 어떤 이유로든 없을 때 그 자리에서 만든다.
     * 쓰기가 일어날 수 있으므로 readOnly 트랜잭션이면 안 된다.
     */
    @Transactional
    public InviteResponse getJoinCode(Long workspaceId, Long userId) {
        workspaceService.requireOwner(workspaceId, userId);
        WorkspaceInvite invite = inviteRepository
                .findByWorkspaceIdAndTypeAndRevokedFalse(workspaceId, InviteType.CODE)
                .orElseGet(() -> inviteRepository.save(WorkspaceInvite.code(
                        workspaceService.findWorkspace(workspaceId),
                        workspaceService.generateUniqueJoinCode(),
                        workspaceService.findUser(userId))));
        return InviteResponse.from(invite);
    }

    /** 코드가 외부에 퍼졌을 때 쓰는 기능. 이전 코드는 즉시 무효가 된다. */
    @Transactional
    public InviteResponse regenerateJoinCode(Long workspaceId, Long userId) {
        workspaceService.requireOwner(workspaceId, userId);
        inviteRepository.findByWorkspaceIdAndTypeAndRevokedFalse(workspaceId, InviteType.CODE)
                .ifPresent(WorkspaceInvite::revoke);

        Workspace workspace = workspaceService.findWorkspace(workspaceId);
        User creator = workspaceService.findUser(userId);
        return InviteResponse.from(inviteRepository.save(
                WorkspaceInvite.code(workspace, workspaceService.generateUniqueJoinCode(), creator)));
    }

    // ---------------------------------------------------------------- 참여

    /**
     * 링크를 열자마자 참여시키지 않고 어디에 들어가는지 먼저 보여준다.
     * 사용자가 모르는 곳에 소속되는 일이 없어야 한다.
     */
    public InvitePreviewResponse preview(String code, Long userId) {
        WorkspaceInvite invite = findInvite(code);
        Workspace workspace = invite.getWorkspace();

        // 이미 멤버인지를 먼저 본다.
        // 일회용 링크는 쓰는 순간 소진되므로, 그 링크로 들어온 사람이 링크를 다시 열면
        // 멤버인데도 "사용할 수 없는 초대"를 보게 된다. 순서를 바꿔 그 상황을 없앤다.
        if (memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId).isPresent()) {
            return new InvitePreviewResponse(workspace.getId(), workspace.getName(), true);
        }
        requireUsable(invite);
        return new InvitePreviewResponse(workspace.getId(), workspace.getName(), false);
    }

    @Transactional
    public JoinResponse accept(String code, Long userId) {
        WorkspaceInvite invite = findInvite(code);
        Workspace workspace = invite.getWorkspace();

        // 이미 멤버면 초대 상태와 무관하게 그냥 그 워크스페이스로 보낸다.
        // 링크를 두 번 누르는 것은 사용자 잘못이 아니고, 사용 횟수도 소모하면 안 된다.
        if (memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId).isPresent()) {
            return new JoinResponse(workspace.getId());
        }

        requireUsable(invite);
        memberRepository.save(WorkspaceMember.join(workspace, workspaceService.findUser(userId), WorkspaceRole.MEMBER));
        invite.use();
        return new JoinResponse(workspace.getId());
    }

    /** 존재하지 않는 코드와 만료된 코드를 같은 에러로 응답한다. 코드 존재 여부를 알려줄 이유가 없다. */
    private WorkspaceInvite findInvite(String code) {
        return inviteRepository.findByCodeWithWorkspace(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_INVALID));
    }

    private void requireUsable(WorkspaceInvite invite) {
        if (!invite.isUsable()) {
            throw new BusinessException(ErrorCode.INVITE_INVALID);
        }
    }
}
