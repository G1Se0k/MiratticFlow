package com.mirattic.flow.chat.service;

import com.mirattic.flow.chat.dto.ChatMessageResponse;
import com.mirattic.flow.chat.entity.ChatMessage;
import com.mirattic.flow.chat.entity.Topic;
import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.chat.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "오너님이 ISSUE-4를 등록했습니다" 같은 활동 알림을 채팅에 남긴다.
 *
 * ChatService 와 나눠 둔 이유는 의존 방향 때문이다.
 * 시스템 메시지는 서버가 스스로 만들기 때문에 권한 검사가 필요 없어 ProjectService 를 모른다.
 * 덕분에 ProjectService 가 이 클래스를 불러도 순환 참조가 생기지 않는다.
 * (ChatService 는 사용자 메시지를 다루므로 권한 검사가 필요하고, 그래서 ProjectService 에 의존한다.)
 */
@Component
@RequiredArgsConstructor
public class SystemMessageSender {

    private final TopicRepository topicRepository;
    private final ChatMessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 프로젝트의 첫 주제("일반")에 남긴다.
     *
     * ponytail: 호출한 쪽 트랜잭션 안에서 바로 브로드캐스트한다.
     * 그 트랜잭션이 뒤에 롤백되면 DB 에는 남지 않는데 화면에는 이미 떠 있다.
     * 지금은 시스템 메시지를 보낸 뒤에 실패할 코드가 없어서 문제가 되지 않는다.
     * 생기면 @TransactionalEventListener(AFTER_COMMIT) 으로 옮긴다.
     */
    @Transactional
    public void send(Long projectId, String content) {
        topicRepository.findFirstByProjectId(projectId).ifPresent(topic -> broadcast(topic, content));
    }

    private void broadcast(Topic topic, String content) {
        ChatMessage saved = messageRepository.save(ChatMessage.system(topic, content));
        messagingTemplate.convertAndSend(
                "/topic/thread/" + topic.getId(), ChatMessageResponse.from(saved));
    }
}
