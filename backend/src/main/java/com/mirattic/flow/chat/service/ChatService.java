package com.mirattic.flow.chat.service;

import com.mirattic.flow.chat.dto.ChatMessageResponse;
import com.mirattic.flow.chat.entity.ChatMessage;
import com.mirattic.flow.chat.entity.Topic;
import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository messageRepository;
    private final TopicService topicService;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 과거 메시지는 REST 로 가져온다. 새 메시지만 WebSocket 으로 받는다.
     * 최신부터 거꾸로 읽은 뒤 화면에 그리기 좋게 뒤집어서 돌려준다.
     */
    public List<ChatMessageResponse> history(Long topicId, Long userId, Long before, int size) {
        topicService.requireAccess(topicId, userId);

        List<ChatMessageResponse> page = messageRepository
                .findPage(topicId, before, Limit.of(Math.min(size, MAX_PAGE_SIZE))).stream()
                .map(ChatMessageResponse::from)
                .toList();

        return page.reversed();
    }

    /** STOMP 로 들어온 메시지를 저장하고 같은 주제를 구독 중인 모두에게 보낸다. */
    @Transactional
    public ChatMessageResponse send(Long topicId, Long userId, String content) {
        Topic topic = topicService.requireAccess(topicId, userId);
        ChatMessage saved = messageRepository.save(
                ChatMessage.user(topic, findUser(userId), content));

        ChatMessageResponse response = ChatMessageResponse.from(saved);
        messagingTemplate.convertAndSend("/topic/thread/" + topicId, response);
        return response;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
