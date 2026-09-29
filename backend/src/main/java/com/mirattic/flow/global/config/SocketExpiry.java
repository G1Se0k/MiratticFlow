package com.mirattic.flow.global.config;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 열린 WebSocket 을 인증에 쓴 access token 의 만료 시각에 닫는다.
 *
 * STOMP 는 CONNECT 에서 한 번만 인증한다. 그대로 두면 구독만 해 두고 프레임을 보내지 않는 연결은
 * 토큰이 만료되거나 Auth 에서 계정이 막힌 뒤에도 비공개 주제의 메시지를 계속 받는다.
 * REST 와 같은 한계(최대 15분)로 맞추려고 연결마다 만료 시각에 닫기를 예약한다 (주기적으로 훑으면 그 주기만큼 늦는다).
 * 클라이언트는 재발급 후 다시 붙는다. CONNECT 를 보내지 않는 연결은 1분 뒤에 닫힌다.
 */
@Slf4j
@Component
public class SocketExpiry {

    private static final Duration UNAUTHENTICATED = Duration.ofMinutes(1);

    /**
     * 자기 스케줄러를 둔다. 스프링의 TaskScheduler 빈을 주입받으면 WebSocket 메시징 설정이 만드는 스케줄러가 걸려
     * 빈 생성 순환이 생긴다 (이 클래스 → 메시징 설정 → StompAuthInterceptor → 이 클래스). 닫기 예약만 하므로 스레드 하나면 된다.
     */
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> closings = new ConcurrentHashMap<>();
    private final Map<String, Long> users = new ConcurrentHashMap<>(); // 세션 id → Flow 사용자 id

    public SocketExpiry() {
        scheduler.setThreadNamePrefix("ws-expiry-");
        scheduler.initialize();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

    void opened(WebSocketSession session) {
        sessions.put(session.getId(), session);
        closeAt(session.getId(), Instant.now().plus(UNAUTHENTICATED));
    }

    void closed(WebSocketSession session) {
        sessions.remove(session.getId());
        users.remove(session.getId());
        ScheduledFuture<?> closing = closings.remove(session.getId());
        if (closing != null) {
            closing.cancel(false);
        }
    }

    /** CONNECT 에서 토큰을 검증한 뒤: 이 연결은 토큰의 exp 까지만 산다. (STOMP 세션 id = WebSocket 세션 id) */
    void authenticated(String sessionId, Long userId, Instant expiresAt) {
        users.put(sessionId, userId);
        closeAt(sessionId, expiresAt);
    }

    /** 탈퇴: 이 사용자의 열린 연결을 바로 닫는다. 이미 구독한 주제의 메시지를 토큰 만료까지 계속 받지 않게. */
    public void closeUser(Long userId) {
        users.forEach((sessionId, owner) -> {
            if (owner.equals(userId)) {
                close(sessionId);
            }
        });
    }

    private void closeAt(String sessionId, Instant when) {
        ScheduledFuture<?> previous = closings.put(sessionId, scheduler.schedule(() -> close(sessionId), when));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void close(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("token expired"));
        } catch (IOException e) {
            log.debug("WebSocket close 실패", e);
        }
    }
}
