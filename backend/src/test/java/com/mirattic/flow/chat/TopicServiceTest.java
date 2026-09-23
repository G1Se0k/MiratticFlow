package com.mirattic.flow.chat;

import com.mirattic.flow.chat.dto.TopicRequest;
import com.mirattic.flow.chat.entity.Topic;
import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.chat.repository.TopicRepository;
import com.mirattic.flow.chat.service.TopicService;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.service.IssueService;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.workspace.entity.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 프로젝트 채팅이 "지워지지도 이름이 바뀌지도 않는다"는 규칙만 따로 검증한다.
 *
 * 시스템 메시지가 갈 곳이라 이게 뚫리면 이슈 등록·상태 변경 알림이 조용히 사라진다.
 * 만든 사람 본인이 시도해도 막혀야 해서, 권한이 아니라 종류로 막는 분기다.
 */
class TopicServiceTest {

    private static final long PROJECT_ID = 10L;
    private static final long CREATOR_ID = 100L;

    private TopicRepository topicRepository;
    private ChatMessageRepository messageRepository;
    private ProjectService projectService;
    private TopicService topicService;

    private Project project;
    private User creator;

    @BeforeEach
    void setUp() {
        topicRepository = mock(TopicRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        projectService = mock(ProjectService.class);

        topicService = new TopicService(topicRepository, messageRepository,
                mock(IssueService.class), projectService, mock(UserRepository.class));

        Workspace workspace = Workspace.create("팀", null);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        creator = User.create("creator@test.com", "encoded", "만든사람");
        ReflectionTestUtils.setField(creator, "id", CREATOR_ID);
        project = Project.create(workspace, "프로젝트", null, creator);
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
    }

    private Topic given(Topic topic) {
        ReflectionTestUtils.setField(topic, "id", 5L);
        when(topicRepository.findByIdWithProject(5L)).thenReturn(Optional.of(topic));
        when(projectService.requireAccess(PROJECT_ID, CREATOR_ID)).thenReturn(project);
        return topic;
    }

    private Topic issueTopic() {
        Issue issue = mock(Issue.class);
        when(issue.getProject()).thenReturn(project);
        return Topic.forIssue(issue, "배포", null, creator);
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("프로젝트 채팅은 만든 사람도 삭제할 수 없다")
    void projectChatCannotBeDeleted() {
        given(Topic.projectChat(project, creator));

        assertThatThrownBy(() -> topicService.delete(5L, CREATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_CHAT_FIXED);

        verify(messageRepository, never()).deleteByTopicId(anyLong());
        verify(topicRepository, never()).delete(any());
    }

    @Test
    @DisplayName("프로젝트 채팅은 이름도 바꿀 수 없다")
    void projectChatCannotBeRenamed() {
        given(Topic.projectChat(project, creator));

        assertThatThrownBy(() -> topicService.update(5L, CREATOR_ID, new TopicRequest("딴 이름", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_CHAT_FIXED);
    }

    @Test
    @DisplayName("이슈 주제는 만든 사람이 지울 수 있고, 메시지를 먼저 지운다")
    void issueTopicIsDeletedWithItsMessages() {
        Topic topic = given(issueTopic());

        topicService.delete(5L, CREATOR_ID);

        // FK 제약 때문에 순서가 중요하다 — 메시지가 먼저다
        var order = inOrder(messageRepository, topicRepository);
        order.verify(messageRepository).deleteByTopicId(5L);
        order.verify(topicRepository).delete(topic);
    }

    @Test
    @DisplayName("만든 사람도 프로젝트 관리자도 아니면 이슈 주제를 지울 수 없다")
    void strangerCannotDeleteIssueTopic() {
        given(issueTopic());
        long strangerId = 300L;
        when(projectService.requireAccess(PROJECT_ID, strangerId)).thenReturn(project);
        when(projectService.canManage(project, strangerId)).thenReturn(false);

        assertThatThrownBy(() -> topicService.delete(5L, strangerId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_TOPIC_MANAGER);
    }
}
