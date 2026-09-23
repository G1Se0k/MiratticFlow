package com.mirattic.flow.project;

import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.chat.repository.TopicRepository;
import com.mirattic.flow.chat.service.SystemMessageSender;
import com.mirattic.flow.comment.repository.CommentRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.service.NotificationSender;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.project.repository.ProjectRepository;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.workspace.entity.Workspace;
import com.mirattic.flow.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 프로젝트 권한 판단만 DB 없이 검증한다.
 *
 * 이 두 메서드(requireAccess / canManage)는 이슈·댓글·주제·채팅이 전부 거쳐 가는 길목이라
 * 분기 하나가 틀리면 네 도메인이 같이 샌다. API 테스트로는 조합마다 준비가 길어져
 * 여기서 경우의 수를 직접 짚는다.
 */
class ProjectServiceTest {

    private static final long WORKSPACE_ID = 1L;
    private static final long PROJECT_ID = 10L;
    private static final long CREATOR_ID = 100L;
    private static final long MEMBER_ID = 200L;
    private static final long OUTSIDER_ID = 300L;

    private ProjectRepository projectRepository;
    private ProjectMemberRepository memberRepository;
    private WorkspaceService workspaceService;
    private ProjectService projectService;
    private Project project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        memberRepository = mock(ProjectMemberRepository.class);
        workspaceService = mock(WorkspaceService.class);

        projectService = new ProjectService(
                projectRepository, memberRepository, workspaceService,
                mock(IssueRepository.class), mock(CommentRepository.class),
                mock(TopicRepository.class), mock(ChatMessageRepository.class),
                mock(NotificationSender.class), mock(SystemMessageSender.class));

        Workspace workspace = Workspace.create("팀", null);
        ReflectionTestUtils.setField(workspace, "id", WORKSPACE_ID);
        User creator = User.create("creator@test.com", "encoded", "만든사람");
        ReflectionTestUtils.setField(creator, "id", CREATOR_ID);

        project = Project.create(workspace, "프로젝트", null, creator);
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        when(projectRepository.findByIdWithDetails(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    // ---------------------------------------------------------------- requireAccess

    @Test
    @DisplayName("프로젝트 참여자는 접근할 수 있다")
    void memberCanAccess() {
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, MEMBER_ID)).thenReturn(true);

        assertThat(projectService.requireAccess(PROJECT_ID, MEMBER_ID)).isSameAs(project);
    }

    @Test
    @DisplayName("참여자가 아니어도 워크스페이스 관리자는 접근할 수 있다")
    void workspaceOwnerCanAccessWithoutJoining() {
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        when(workspaceService.isOwner(WORKSPACE_ID, OUTSIDER_ID)).thenReturn(true);

        assertThat(projectService.requireAccess(PROJECT_ID, OUTSIDER_ID)).isSameAs(project);
    }

    @Test
    @DisplayName("참여자도 워크스페이스 관리자도 아니면 막힌다")
    void outsiderIsBlocked() {
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID)).thenReturn(false);
        when(workspaceService.isOwner(WORKSPACE_ID, OUTSIDER_ID)).thenReturn(false);

        assertThatThrownBy(() -> projectService.requireAccess(PROJECT_ID, OUTSIDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_PROJECT_MEMBER);
    }

    @Test
    @DisplayName("없는 프로젝트는 권한을 따지기 전에 404 다")
    void missingProject() {
        when(projectRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.requireAccess(999L, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_NOT_FOUND);
    }

    // ---------------------------------------------------------------- canManage

    @Test
    @DisplayName("관리 권한은 만든 사람과 워크스페이스 관리자에게만 있다")
    void canManage() {
        when(workspaceService.isOwner(WORKSPACE_ID, MEMBER_ID)).thenReturn(false);
        when(workspaceService.isOwner(WORKSPACE_ID, OUTSIDER_ID)).thenReturn(true);

        assertThat(projectService.canManage(project, CREATOR_ID)).isTrue();   // 만든 사람
        assertThat(projectService.canManage(project, OUTSIDER_ID)).isTrue();  // 워크스페이스 관리자
        assertThat(projectService.canManage(project, MEMBER_ID)).isFalse();   // 그냥 참여자
    }

    @Test
    @DisplayName("참여자여도 관리자가 아니면 requireManager 에서 막힌다")
    void memberIsNotManager() {
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, MEMBER_ID)).thenReturn(true);
        when(workspaceService.isOwner(WORKSPACE_ID, MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> projectService.requireManager(PROJECT_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_PROJECT_MANAGER);
    }
}
