package com.mirattic.flow.chat.controller;

import com.mirattic.flow.chat.dto.SendMessageRequest;
import com.mirattic.flow.chat.service.ChatService;
import com.mirattic.flow.global.security.StompPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * 메시지 전송만 WebSocket 으로 받는다. 조회는 ChatController(REST) 가 맡는다.
 * 브로드캐스트는 ChatService 가 SimpMessagingTemplate 으로 직접 하므로
 * @SendTo 를 쓰지 않는다 — 저장된 id 와 시각이 담긴 응답을 보내야 하기 때문이다.
 */
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;

    @MessageMapping("/thread/{topicId}")
    public void send(@DestinationVariable Long topicId,
                     @Valid SendMessageRequest request,
                     StompPrincipal principal) {
        chatService.send(topicId, principal.userId(), request.content());
    }
}
