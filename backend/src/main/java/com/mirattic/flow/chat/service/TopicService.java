package com.mirattic.flow.chat.service;

import com.mirattic.flow.chat.dto.TopicRequest;
import com.mirattic.flow.chat.dto.TopicResponse;
import com.mirattic.flow.chat.entity.Topic;
import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.chat.repository.TopicRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.service.IssueService;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final ChatMessageRepository messageRepository;
    private final IssueService issueService;
    private final ProjectService projectService;
    private final UserRepository userRepository;

    /**
     * 주제를 볼 수 있는가 = 그 주제가 속한 프로젝트에 접근할 수 있는가.
     * 채팅 메시지와 STOMP 구독 권한 검사도 이 메서드를 거친다.
     */
    public Topic requireAccess(Long topicId, Long userId) {
        Topic topic = topicRepository.findByIdWithProject(topicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
        projectService.requireAccess(topic.getProject().getId(), userId);
        return topic;
    }

    /** 프로젝트 채팅 하나. 프로젝트를 만들 때 같이 생기므로 없을 수 없다. */
    public TopicResponse findProjectChat(Long projectId, Long userId) {
        projectService.requireAccess(projectId, userId);
        Topic topic = topicRepository.findProjectChat(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
        return TopicResponse.of(topic, false); // 프로젝트 채팅은 누구도 지울 수 없다
    }

    public List<TopicResponse> findAllByIssue(Long issueId, Long userId) {
        issueService.requireReadable(issueId, userId);
        return topicRepository.findAllByIssueId(issueId).stream()
                .map(topic -> TopicResponse.of(topic, canManage(topic, userId)))
                .toList();
    }

    @Transactional
    public TopicResponse create(Long issueId, Long userId, TopicRequest request) {
        Issue issue = issueService.requireReadable(issueId, userId);
        Topic topic = topicRepository.save(
                Topic.forIssue(issue, request.name(), request.description(), findUser(userId)));
        return TopicResponse.of(topic, true);
    }

    @Transactional
    public TopicResponse update(Long topicId, Long userId, TopicRequest request) {
        Topic topic = requireManager(topicId, userId);
        topic.update(request.name(), request.description());
        return TopicResponse.of(topic, true);
    }

    @Transactional
    public void delete(Long topicId, Long userId) {
        Topic topic = requireManager(topicId, userId);
        messageRepository.deleteByTopicId(topicId); // 자식 먼저 — FK 제약
        topicRepository.delete(topic);
    }

    private Topic requireManager(Long topicId, Long userId) {
        Topic topic = requireAccess(topicId, userId);
        // 프로젝트 채팅은 시스템 메시지가 가는 곳이라 이름 변경도 삭제도 막는다.
        if (topic.isProjectChat()) {
            throw new BusinessException(ErrorCode.PROJECT_CHAT_FIXED);
        }
        if (!canManage(topic, userId)) {
            throw new BusinessException(ErrorCode.NOT_TOPIC_MANAGER);
        }
        return topic;
    }

    private boolean canManage(Topic topic, Long userId) {
        return !topic.isProjectChat()
                && (topic.isCreatedBy(userId)
                || projectService.canManage(topic.getProject(), userId));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
