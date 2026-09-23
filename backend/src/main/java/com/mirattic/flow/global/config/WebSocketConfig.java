package com.mirattic.flow.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 핸드셰이크는 익명으로 통과시킨다. 인증은 CONNECT 프레임에서 한다.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");   // 서버 → 클라이언트 구독 경로
        registry.setApplicationDestinationPrefixes("/app"); // 클라이언트 → 서버 전송 경로
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
