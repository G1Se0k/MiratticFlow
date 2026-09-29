package com.mirattic.flow.global.config;

import com.mirattic.flow.auth.service.AuthCookies;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * STOMP 설정.
 *
 * 브로커는 스프링이 메모리에 띄우는 SimpleBroker 를 쓴다.
 * RabbitMQ 같은 외부 브로커는 서버를 여러 대로 늘릴 때 필요한데,
 * 지금은 단일 인스턴스 배포라 운영할 것만 늘어난다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;
    private final SocketExpiry socketExpiry;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 핸드셰이크에서는 쿠키의 access token 을 세션 속성으로 옮기기만 한다. 검증은 CONNECT 프레임에서 한다.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).addInterceptors(new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler handler, Map<String, Object> attributes) {
                if (request instanceof ServletServerHttpRequest servlet) {
                    String token = AuthCookies.read(servlet.getServletRequest(), AuthCookies.ACCESS);
                    if (token != null) {
                        attributes.put(StompAuthInterceptor.TOKEN_ATTRIBUTE, token);
                    }
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler handler, Exception exception) {
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");   // 서버 → 클라이언트 구독 경로
        registry.setApplicationDestinationPrefixes("/app"); // 클라이언트 → 서버 전송 경로
    }

    /** 연결마다 SocketExpiry 에 등록해 두고, 토큰 만료 시각에 닫는다. */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                socketExpiry.opened(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                socketExpiry.closed(session);
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
