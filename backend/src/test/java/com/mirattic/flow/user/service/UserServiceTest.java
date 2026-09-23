package com.mirattic.flow.user.service;

import com.mirattic.flow.auth.repository.RefreshTokenRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.repository.NotificationRepository;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.user.entity.AuthProvider;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.workspace.entity.Workspace;
import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import com.mirattic.flow.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** DB 없이 탈퇴 규칙만 검증한다. */
class UserServiceTest {

    private static final Long USER_ID = 1L;

    private UserRepository userRepository;
    private WorkspaceMemberRepository workspaceMemberRepository;
    private ProjectMemberRepository projectMemberRepository;
    private NotificationRepository notificationRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private IssueRepository issueRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        issueRepository = mock(IssueRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, workspaceMemberRepository, projectMemberRepository,
                notificationRepository, refreshTokenRepository, issueRepository, passwordEncoder);
    }

    private User given(User user) {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUserIdAndRole(USER_ID, WorkspaceRole.OWNER)).thenReturn(List.of());
        return user;
    }

    private User localUser(String rawPassword) {
        return given(User.create("test@example.com", passwordEncoder.encode(rawPassword), "테스터"));
    }

    @Test
    @DisplayName("탈퇴하면 이메일과 비밀번호, 소셜 식별자가 비워지고 이름이 바뀐다")
    void withdraw() {
        User user = localUser("password1!");

        userService.withdraw(USER_ID, "password1!");

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
    }

    @Test
    @DisplayName("탈퇴하면 담당 이슈가 해제되고 멤버십·알림·리프레시 토큰이 지워진다")
    void withdrawCleansUp() {
        User user = localUser("password1!");

        userService.withdraw(USER_ID, "password1!");

        verify(issueRepository).unassignByUserId(USER_ID);
        verify(notificationRepository).deleteByUserId(USER_ID);
        verify(projectMemberRepository).deleteByUserId(USER_ID);
        verify(workspaceMemberRepository).deleteByUserId(USER_ID);
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다")
    void withdrawWithWrongPassword() {
        localUser("password1!");

        assertThatThrownBy(() -> userService.withdraw(USER_ID, "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_MISMATCH);

        verify(workspaceMemberRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("소셜 회원은 비밀번호 없이 탈퇴할 수 있다")
    void withdrawSocialUser() {
        User user = given(User.createSocial(AuthProvider.KAKAO, "kakao-1", null, "카카오테스터"));

        userService.withdraw(USER_ID, null);

        assertThat(user.isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("혼자 관리자인 워크스페이스가 있으면 탈퇴할 수 없다")
    void withdrawWithSoleOwnedWorkspace() {
        User user = localUser("password1!");
        Workspace workspace = Workspace.create("워크스페이스", null);
        ReflectionTestUtils.setField(workspace, "id", 10L);
        WorkspaceMember owner = WorkspaceMember.join(workspace, user, WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findAllByUserIdAndRole(USER_ID, WorkspaceRole.OWNER))
                .thenReturn(List.of(owner));
        when(workspaceMemberRepository.countByWorkspaceIdAndRole(10L, WorkspaceRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> userService.withdraw(USER_ID, "password1!"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OWNER_WORKSPACE_EXISTS);

        assertThat(user.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("관리자가 더 있는 워크스페이스는 탈퇴를 막지 않는다")
    void withdrawWithAnotherOwner() {
        User user = localUser("password1!");
        Workspace workspace = Workspace.create("워크스페이스", null);
        ReflectionTestUtils.setField(workspace, "id", 10L);
        when(workspaceMemberRepository.findAllByUserIdAndRole(USER_ID, WorkspaceRole.OWNER))
                .thenReturn(List.of(WorkspaceMember.join(workspace, user, WorkspaceRole.OWNER)));
        when(workspaceMemberRepository.countByWorkspaceIdAndRole(10L, WorkspaceRole.OWNER)).thenReturn(2L);

        userService.withdraw(USER_ID, "password1!");

        assertThat(user.isWithdrawn()).isTrue();
    }
}
