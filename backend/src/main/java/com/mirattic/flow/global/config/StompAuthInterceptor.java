package com.mirattic.flow.global.config;

import com.mirattic.flow.chat.service.TopicService;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.security.StompPrincipal;
import com.mirattic.flow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * WebSocket 인증과 권한 검사.
 *
 * 브라우저는 access token 을 HttpOnly 쿠키(flow_at)로만 갖고 있어 스크립트가 읽을 수 없다.
 * 대신 같은 출처의 WebSocket 핸드셰이크에는 쿠키가 붙으므로, WebSocketConfig 가 핸드셰이크에서 쿠키 값을
 * 세션 속성으로 옮겨 두고 여기 CONNECT 에서 검증한다. 테스트 · 도구는 CONNECT 헤더의 Bearer 토큰을 쓴다.
 *
 * 검증에는 REST 와 같은 JwtDecoder(Mirattic Auth JWKS)를 쓴다 — 인증 방식이 둘로 갈라지지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String SUBSCRIBE_PREFIX = "/topic/thread/";
    private static final String SEND_PREFIX = "/app/thread/";

    /** 핸드셰이크에서 쿠키의 access token 을 옮겨 두는 세션 속성 이름 (WebSocketConfig). */
    public static final String TOKEN_ATTRIBUTE = "flow_at";

    private final JwtDecoder jwtDecoder;
    /** 지연 조회: UserService 를 바로 주입하면 TopicService 와 같은 빈 생성 순환이 생긴다 (아래 설명). */
    private final ObjectProvider<UserService> userService;
    private final SocketExpiry socketExpiry;

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
        if (token == null && accessor.getSessionAttributes() != null
                && accessor.getSessionAttributes().get(TOKEN_ATTRIBUTE) instanceof String cookie) {
            token = cookie;
        }
        if (token == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = userService.getObject().resolve(jwt)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        accessor.setUser(new StompPrincipal(userId));
        socketExpiry.authenticated(accessor.getSessionId(), userId, jwt.getExpiresAt());
    }

    private void requireTopicAccess(StompHeaderAccessor accessor, String prefix) {
        String destination = accessor.getDestination();
        // 허용 목록: 우리 주제만 구독·전송할 수 있다. /topic/** 같은 와일드카드 구독이나
        // 브로커(/topic)로 직접 보내는 SEND 가 권한 검사를 건너뛰지 못하게 한다.
        if (destination == null || !destination.startsWith(prefix)) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
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
