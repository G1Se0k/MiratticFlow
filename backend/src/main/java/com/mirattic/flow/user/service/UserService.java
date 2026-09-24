package com.mirattic.flow.user.service;

import com.mirattic.flow.auth.repository.RefreshTokenRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.repository.NotificationRepository;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.user.dto.UpdateUserRequest;
import com.mirattic.flow.user.dto.UserResponse;
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
     * 계정 정보 수정. 보낸 필드만 바꾼다.
     *
     * 이름은 언제든 바꿀 수 있지만, 이메일과 비밀번호는 로그인 수단이라 현재 비밀번호를 다시 확인한다.
     * 액세스 토큰만 훔친 사람이 이메일을 자기 것으로 바꿔 계정을 가져가는 것을 막는다
     * (JWT 는 서버에서 무효화할 수 없으니 토큰 소유만으로 로그인 수단을 못 바꾸게 해야 한다).
     */
    @Transactional
    public UserResponse update(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ((request.changesEmail() || request.changesPassword()) && !user.hasPassword()) {
            // 소셜 회원은 비밀번호가 없어 본인 확인을 할 수단이 없고, 이메일은 제공자가 준 식별 정보다.
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_IMMUTABLE);
        }
        if (request.changesEmail() || request.changesPassword()) {
            requirePassword(user, request.currentPassword());
        }

        if (request.name() != null) {
            // @Size(min = 1) 은 공백만 있는 이름을 통과시킨다. 화면에 빈 자리로 보이는 이름은 없는 것과 같다.
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            user.changeName(name);
        }
        if (request.changesEmail() && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
            user.changeEmail(request.email());
        }
        if (request.changesPassword()) {
            user.changePassword(passwordEncoder.encode(request.newPassword()));
            // 비밀번호를 바꾼 이유가 유출일 수 있다. 남은 세션이 계속 재발급을 받으면 바꾼 의미가 없다.
            refreshTokenRepository.deleteByUser(user);
        }
        return UserResponse.from(user);
    }

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
