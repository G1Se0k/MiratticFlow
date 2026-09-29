package com.mirattic.flow.user.service;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.repository.NotificationRepository;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.user.dto.UpdateUserRequest;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.entity.Withdrawal;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.user.repository.WithdrawalRepository;
import com.mirattic.flow.workspace.entity.Workspace;
import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import com.mirattic.flow.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** DB 없이 로그인 연결 · 탈퇴 · 이름 변경 규칙만 검증한다. */
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String UID = "4b8e2c3a-0000-4000-8000-000000000001";

    private UserRepository userRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private ProjectMemberRepository projectMemberRepository;
    private NotificationRepository notificationRepository;
    private IssueRepository issueRepository;
    private WithdrawalRepository withdrawalRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        issueRepository = mock(IssueRepository.class);
        withdrawalRepository = mock(WithdrawalRepository.class);
        userService = new UserService(userRepository, workspaceMemberRepository, projectMemberRepository,
                notificationRepository, issueRepository, withdrawalRepository);
    }

    private User given(User user) {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUserIdAndRole(USER_ID, WorkspaceRole.OWNER)).thenReturn(List.of());
        return user;
    }

    private User user() {
        return given(User.create(UID, "test@example.com", "테스터", 1_000L));
    }

    // ---------------------------------------------------------------- Mirattic 계정 로그인

    @Test
    @DisplayName("처음 보는 Mirattic 계정이면 Flow 사용자를 만든다")
    void signInCreatesUser() {
        when(userRepository.findByMiratticUid(UID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        assertThat(userService.signIn(UID, "a@example.com", "처음", java.time.Instant.ofEpochSecond(1_000))).isEqualTo(7L);
        verify(userRepository).save(argThat(u -> UID.equals(u.getMiratticUid()) && "처음".equals(u.getName())
                && u.getEnrolledAuthTime() == 1_000L));
    }

    @Test
    @DisplayName("다시 로그인하면 같은 사용자 — 이메일 사본은 갱신하고 Flow 에서 바꾼 이름은 지킨다")
    void signInReusesUser() {
        User user = user();
        user.changeName("Flow에서 바꾼 이름");
        when(userRepository.findByMiratticUid(UID)).thenReturn(Optional.of(user));

        assertThat(userService.signIn(UID, "new@example.com", "Auth 이름", java.time.Instant.now())).isEqualTo(USER_ID);
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getName()).isEqualTo("Flow에서 바꾼 이름");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴했던 계정은 탈퇴보다 뒤의 로그인이어야 다시 사용자를 만든다 (예전 SSO 세션으로는 안 된다)")
    void rejoinNeedsALoginAfterTheWithdrawal() {
        when(userRepository.findByMiratticUid(UID)).thenReturn(Optional.empty());
        when(withdrawalRepository.findById(any())).thenReturn(Optional.of(new Withdrawal("h", 2_000L)));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> userService.signIn(UID, null, "예전 세션", java.time.Instant.ofEpochSecond(2_000)))
                .isInstanceOf(FreshLoginRequired.class);
        userService.signIn(UID, null, "새 로그인", java.time.Instant.ofEpochSecond(2_001));
        verify(userRepository).save(argThat(u -> u.getEnrolledAuthTime() == 2_001L));
    }

    // ---------------------------------------------------------------- 탈퇴

    @Test
    @DisplayName("탈퇴하면 Mirattic UID · 이메일이 비워지고 이름이 바뀐다 (같은 계정으로 다시 오면 새 사용자)")
    void withdraw() {
        User user = user();

        userService.withdraw(USER_ID);

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getMiratticUid()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        // 다시 가입할 때의 기준으로 탈퇴 시각을 남긴다 (UID 는 해시로만).
        verify(withdrawalRepository).save(argThat(w -> w.getMiratticUidHash().length() == 64 && w.getWithdrawnAt() > 0));
    }

    @Test
    @DisplayName("탈퇴하면 담당 이슈가 해제되고 멤버십 · 알림이 지워진다")
    void withdrawCleansUp() {
        user();

        userService.withdraw(USER_ID);

        verify(issueRepository).unassignByUserId(USER_ID);
        verify(notificationRepository).deleteByUserId(USER_ID);
        verify(projectMemberRepository).deleteByUserId(USER_ID);
        verify(workspaceMemberRepository).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("혼자 관리자인 워크스페이스가 있으면 탈퇴할 수 없다")
    void withdrawWithSoleOwnedWorkspace() {
        User user = user();
        Workspace workspace = Workspace.create("워크스페이스", null);
        ReflectionTestUtils.setField(workspace, "id", 10L);
        WorkspaceMember owner = WorkspaceMember.join(workspace, user, WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findAllByUserIdAndRole(USER_ID, WorkspaceRole.OWNER))
                .thenReturn(List.of(owner));
        when(workspaceMemberRepository.countByWorkspaceIdAndRole(10L, WorkspaceRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OWNER_WORKSPACE_EXISTS);

        assertThat(user.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("관리자가 더 있는 워크스페이스는 탈퇴를 막지 않는다")
    void withdrawWithAnotherOwner() {
        User user = user();
        Workspace workspace = Workspace.create("워크스페이스", null);
        ReflectionTestUtils.setField(workspace, "id", 10L);
        when(workspaceMemberRepository.findAllByUserIdAndRole(USER_ID, WorkspaceRole.OWNER))
                .thenReturn(List.of(WorkspaceMember.join(workspace, user, WorkspaceRole.OWNER)));
        when(workspaceMemberRepository.countByWorkspaceIdAndRole(10L, WorkspaceRole.OWNER)).thenReturn(2L);

        userService.withdraw(USER_ID);

        assertThat(user.isWithdrawn()).isTrue();
    }

    // ---------------------------------------------------------------- 이름 변경

    @Test
    @DisplayName("이름은 앞뒤 공백을 떼고 바꾼다")
    void changeName() {
        User user = user();

        assertThat(userService.update(USER_ID, new UpdateUserRequest("  새 이름 ")).name()).isEqualTo("새 이름");
        assertThat(user.getName()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("공백뿐인 이름은 거절한다")
    void blankNameRejected() {
        user();

        assertThatThrownBy(() -> userService.update(USER_ID, new UpdateUserRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }
}
