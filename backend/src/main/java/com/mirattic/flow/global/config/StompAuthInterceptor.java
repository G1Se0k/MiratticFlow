package com.mirattic.flow.global.config;

import com.mirattic.flow.chat.service.TopicService;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.security.JwtProvider;
import com.mirattic.flow.global.security.StompPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 인증과 권한 검사.
 *
 * 브라우저의 WebSocket API 는 핸드셰이크에 커스텀 헤더를 붙일 수 없어서
 * REST 처럼 Authorization 헤더를 쓸 수 없다. 토큰을 쿼리스트링에 실으면
 * 접근 로그에 그대로 남는다. 그래서 HTTP 핸드셰이크는 익명으로 통과시키고
 * 그 위의 STOMP CONNECT 프레임 헤더로 인증한다.
 *
 * 검증에는 REST 와 같은 JwtProvider 를 쓴다 — 인증 방식이 둘로 갈라지지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String SUBSCRIBE_PREFIX = "/topic/thread/";
    private static final String SEND_PREFIX = "/app/thread/";

    private final JwtProvider jwtProvider;

    /**
     * TopicService 를 바로 주입받으면 빈 생성 순환이 생긴다.
     *   이 인터셉터 → TopicService → ProjectService → SystemMessageSender
     *   → SimpMessagingTemplate → WebSocket 설정 → 이 인터셉터
     * 메시징 기반 구조는 애플리케이션 서비스보다 먼저 만들어져야 하는데,
     * 우리는 그 안에서 서비스를 쓰려 하기 때문이다.
     * 프레임이 들어오는 시점에는 이미 모든 빈이 준비되어 있으므로 그때 꺼내 쓴다.
     */
    private final ObjectProvider<TopicService> topicServiceProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            // 인증만 하고 끝내면 로그인한 사람 누구나 남의 주제를 구독할 수 있다.
            case SUBSCRIBE -> requireTopicAccess(accessor, SUBSCRIBE_PREFIX);
            // 목적지를 클라이언트가 보내므로 전송할 때도 다시 확인한다.
            case SEND -> requireTopicAccess(accessor, SEND_PREFIX);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HEADER);
        String token = (header != null && header.startsWith(PREFIX)) ? header.substring(PREFIX.length()) : null;

        if (token == null || !jwtProvider.isValid(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        accessor.setUser(new StompPrincipal(jwtProvider.getUserId(token)));
    }

    private void requireTopicAccess(StompHeaderAccessor accessor, String prefix) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(prefix)) {
            return; // 우리가 관리하는 목적지가 아니면 관여하지 않는다
        }
        topicServiceProvider.getObject()
                .requireAccess(parseTopicId(destination, prefix), currentUserId(accessor));
    }

    private Long parseTopicId(String destination, String prefix) {
        try {
            return Long.parseLong(destination.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal.userId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
