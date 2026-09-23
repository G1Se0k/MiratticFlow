package com.mirattic.flow.chat;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.chat.dto.ChatMessageResponse;
import com.mirattic.flow.chat.dto.SendMessageRequest;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.workspace.dto.WorkspaceRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 STOMP 클라이언트를 붙여 확인한다.
 * WebSocket 은 조용히 깨지기 쉬워서, 인증·구독 권한·브로드캐스트를 눈으로 볼 수 있는 형태로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatStompTest {

    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;

    private RestTemplate rest;
    private String baseUrl;
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        baseUrl = "http://localhost:" + port;
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    // ---------------------------------------------------------------- 헬퍼

    private String post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getBody();
    }

    private String get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    private String newUserToken() {
        String email = "s" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        post("/api/auth/signup", new SignupRequest(email, "password123", "테스터"), null);
        String body = post("/api/auth/login", new LoginRequest(email, "password123"), null);
        return objectMapper.readTree(body).get("accessToken").asString();
    }

    private long id(String body) {
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** 워크스페이스 → 프로젝트를 만들고 자동 생성된 프로젝트 채팅의 id 를 돌려준다. */
    private long setUpTopic(String token) {
        long workspaceId = id(post("/api/workspaces", new WorkspaceRequest("팀", null), token));
        long projectId = id(post("/api/workspaces/" + workspaceId + "/projects",
                new ProjectRequest("프로젝트", null, null), token));
        return id(get("/api/projects/" + projectId + "/chat", token));
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new org.springframework.web.socket.WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
    }

    /** 구독한 메시지를 담아둘 큐. 비동기로 오므로 큐에서 기다린다. */
    private BlockingQueue<ChatMessageResponse> subscribe(StompSession session, long topicId) {
        BlockingQueue<ChatMessageResponse> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/thread/" + topicId, new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return ChatMessageResponse.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                received.add((ChatMessageResponse) payload);
            }
        });
        return received;
    }

    // ---------------------------------------------------------------- 인증

    @Test
    @DisplayName("토큰 없이는 STOMP 연결이 거부된다")
    void connectWithoutToken() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("유효하지 않은 토큰도 거부된다")
    void connectWithBrokenToken() {
        assertThatThrownBy(() -> connect("not-a-real-token"))
                .isInstanceOf(ExecutionException.class);
    }

    // ---------------------------------------------------------------- 구독 권한

    @Test
    @DisplayName("프로젝트 참여자가 아니면 주제를 구독할 수 없다")
    void outsiderCannotSubscribe() throws Exception {
        long topicId = setUpTopic(newUserToken());

        StompSession session = connect(newUserToken()); // 연결은 된다 — 로그인은 했으니까
        subscribe(session, topicId);

        // 구독이 거절되면 서버가 세션을 끊는다
        Thread.sleep(500);
        assertThat(session.isConnected()).isFalse();
    }

    // ---------------------------------------------------------------- 송수신

    @Test
    @DisplayName("보낸 메시지가 같은 주제를 구독한 쪽에 도착하고 DB 에도 남는다")
    void sendAndReceive() throws Exception {
        String token = newUserToken();
        long topicId = setUpTopic(token);

        StompSession session = connect(token);
        BlockingQueue<ChatMessageResponse> received = subscribe(session, topicId);
        Thread.sleep(300); // 구독이 자리잡을 때까지

        session.send("/app/thread/" + topicId, new SendMessageRequest("배포 언제 하나요?"));

        ChatMessageResponse message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.content()).isEqualTo("배포 언제 하나요?");
        assertThat(message.senderName()).isEqualTo("테스터");
        assertThat(message.type().name()).isEqualTo("USER");
        assertThat(message.id()).isNotNull(); // 저장된 뒤에 나간다

        // 과거 메시지 조회(REST)에도 그대로 있다
        String history = get("/api/topics/" + topicId + "/messages", token);
        assertThat(objectMapper.readTree(history).get(0).get("content").asString())
                .isEqualTo("배포 언제 하나요?");
    }

    @Test
    @DisplayName("이슈를 등록하면 구독 중인 사람에게 시스템 메시지가 바로 간다")
    void systemMessageIsBroadcast() throws Exception {
        String token = newUserToken();
        long workspaceId = id(post("/api/workspaces", new WorkspaceRequest("팀", null), token));
        long projectId = id(post("/api/workspaces/" + workspaceId + "/projects",
                new ProjectRequest("프로젝트", null, null), token));
        long topicId = id(get("/api/projects/" + projectId + "/chat", token));

        StompSession session = connect(token);
        BlockingQueue<ChatMessageResponse> received = subscribe(session, topicId);
        Thread.sleep(300);

        post("/api/projects/" + projectId + "/issues",
                new com.mirattic.flow.issue.dto.IssueRequest("로그인 오류", null, null, null, null, null), token);

        ChatMessageResponse message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.type().name()).isEqualTo("SYSTEM");
        assertThat(message.senderName()).isNull();
        assertThat(message.content()).isEqualTo("테스터님이 ISSUE-1를 등록했습니다.");
    }
}
