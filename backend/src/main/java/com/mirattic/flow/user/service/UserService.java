package com.mirattic.flow.user.service;

import com.mirattic.flow.auth.repository.RefreshTokenRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.repository.NotificationRepository;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import com.mirattic.flow.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final IssueRepository issueRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원 탈퇴.
     *
     * 이 사용자를 참조하는 이슈 · 댓글 · 채팅이 남아 있어 users 행 자체는 지울 수 없다.
     * 지운다면 탈퇴한 사람의 기록만이 아니라 함께 일한 팀의 기록까지 사라진다.
     * 그래서 행은 남기고 개인정보 컬럼을 비우며(User.withdraw), 본인에게만 속한 것들은 실제로 지운다.
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        requirePassword(user, password);
        requireNoSoleOwnedWorkspace(userId);

        // 개인정보 파기가 먼저다.
        // 아래 벌크 쿼리들은 clearAutomatically = true 라 실행 후 영속성 컨텍스트를 비운다.
        // 그 뒤에 엔티티를 고치면 이미 준영속 상태여서 변경 감지가 일어나지 않고,
        // 멤버십만 지워진 채 이메일과 비밀번호가 그대로 남는다 (204 를 받고도 파기되지 않는다).
        user.withdraw();
        userRepository.saveAndFlush(user);

        issueRepository.unassignByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        projectMemberRepository.deleteByUserId(userId);
        workspaceMemberRepository.deleteByUserId(userId);
        // 남아 있으면 탈퇴 후에도 토큰을 재발급받을 수 있다.
        refreshTokenRepository.deleteByUser(user);
    }

    /** 토큰만 손에 넣으면 남의 계정을 지울 수 있어서는 안 된다. 되돌릴 수 없는 동작이므로 다시 확인한다. */
    private void requirePassword(User user, String password) {
        if (!user.hasPassword()) {
            return; // 소셜 회원은 확인할 비밀번호가 없다. 화면의 확인 절차로 갈음한다.
        }
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    /**
     * 관리자가 나 하나뿐인 워크스페이스를 남기고 떠날 수는 없다.
     * 워크스페이스를 나갈 때(leave)와 같은 규칙이다 — 주인이 없으면 아무도 그 워크스페이스를 손댈 수 없다.
     */
    private void requireNoSoleOwnedWorkspace(Long userId) {
        List<WorkspaceMember> owned = workspaceMemberRepository.findAllByUserIdAndRole(userId, WorkspaceRole.OWNER);
        for (WorkspaceMember member : owned) {
            long owners = workspaceMemberRepository.countByWorkspaceIdAndRole(
                    member.getWorkspace().getId(), WorkspaceRole.OWNER);
            if (owners <= 1) {
                throw new BusinessException(ErrorCode.OWNER_WORKSPACE_EXISTS);
            }
        }
    }
}
