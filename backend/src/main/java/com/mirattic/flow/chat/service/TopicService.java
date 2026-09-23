package com.mirattic.flow.chat.service;

import com.mirattic.flow.chat.dto.TopicRequest;
import com.mirattic.flow.chat.dto.TopicResponse;
import com.mirattic.flow.chat.entity.Topic;
import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.chat.repository.TopicRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.project.entity.Project;
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

    public List<TopicResponse> findAll(Long projectId, Long userId) {
        projectService.requireAccess(projectId, userId);
        return topicRepository.findAllByProjectIdWithCreator(projectId).stream()
                .map(topic -> TopicResponse.of(topic, canManage(topic, userId)))
                .toList();
    }

    @Transactional
    public TopicResponse create(Long projectId, Long userId, TopicRequest request) {
        Project project = projectService.requireAccess(projectId, userId);
        Topic topic = topicRepository.save(
                Topic.create(project, request.name(), request.description(), findUser(userId)));
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
        // 주제가 하나도 없으면 시스템 메시지가 갈 곳이 사라진다.
        if (topicRepository.countByProjectId(topic.getProject().getId()) <= 1) {
            throw new BusinessException(ErrorCode.LAST_TOPIC);
        }
        messageRepository.deleteByTopicId(topicId); // 자식 먼저 — FK 제약
        topicRepository.delete(topic);
    }

    private Topic requireManager(Long topicId, Long userId) {
        Topic topic = requireAccess(topicId, userId);
        if (!canManage(topic, userId)) {
            throw new BusinessException(ErrorCode.NOT_TOPIC_MANAGER);
        }
        return topic;
    }

    private boolean canManage(Topic topic, Long userId) {
        return topic.isCreatedBy(userId) || projectService.canManage(topic.getProject(), userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
