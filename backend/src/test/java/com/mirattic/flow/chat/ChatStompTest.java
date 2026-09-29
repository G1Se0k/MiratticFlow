package com.mirattic.flow.chat;

import com.mirattic.flow.chat.dto.ChatMessageResponse;
import com.mirattic.flow.chat.dto.SendMessageRequest;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.global.config.SocketExpiry;
import com.mirattic.flow.support.AuthStub;
import com.mirattic.flow.user.service.UserService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    @DynamicPropertySource
    static void auth(DynamicPropertyRegistry registry) {
        registry.add("app.auth.issuer", AuthStub::issuer);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired SocketExpiry socketExpiry;

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

    /** Mirattic 계정으로 로그인한 사용자 (로그인 흐름 자체는 AuthApiTest 가 검증한다). */
    private String newUserToken() {
        String uid = UUID.randomUUID().toString();
        userService.signIn(uid, "s" + uid.substring(0, 8) + "@test.com", "테스터", java.time.Instant.now());
        return AuthStub.accessToken(uid);
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
    @DisplayName("브라우저처럼 핸드셰이크의 flow_at 쿠키로도 연결된다 (CONNECT 헤더 없이)")
    void connectWithCookie() throws Exception {
        String token = newUserToken();
        org.springframework.web.socket.WebSocketHttpHeaders handshake = new org.springframework.web.socket.WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, "flow_at=" + token);
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws", handshake, new StompHeaders(),
                        new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("연결은 인증에 쓴 토큰의 만료 시각에 서버가 끊는다 (구독만 해 둔 연결도)")
    void connectionClosesWhenTheTokenExpires() throws Exception {
        String uid = UUID.randomUUID().toString();
        userService.signIn(uid, null, "곧만료", java.time.Instant.now());
        StompSession session = connect(AuthStub.accessToken(uid, 2));
        assertThat(session.isConnected()).isTrue();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 50 && session.isConnected(); i++) {
            Thread.sleep(100);
        }
        assertThat(session.isConnected()).isFalse();
        // 주기적으로 훑지 않고 exp 에 맞춰 닫는다 (JWT exp 는 초 단위라 최대 1초 이르거나 늦다).
        assertThat(System.currentTimeMillis() - start).isLessThan(3500);
    }

    @Test
    @DisplayName("탈퇴하면 이미 열려 있는 연결도 바로 끊긴다 (탈퇴 흐름은 UserApiTest)")
    void withdrawalClosesOpenConnections() throws Exception {
        String uid = UUID.randomUUID().toString();
        Long userId = userService.signIn(uid, null, "곧탈퇴", java.time.Instant.now());
        StompSession session = connect(AuthStub.accessToken(uid));
        // 탈퇴 콜백(LoginController)이 하는 일과 같은 순서
        userService.withdraw(userId);
        socketExpiry.closeUser(userId);
        for (int i = 0; i < 30 && session.isConnected(); i++) {
            Thread.sleep(100);
        }
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("로그아웃하면 다른 탭의 연결도 바로 끊긴다")
    void logoutClosesOtherTabsConnections() throws Exception {
        String token = newUserToken();
        StompSession otherTab = connect(token);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "flow_at=" + token);
        headers.add("X-Requested-With", "flow");
        rest.exchange(baseUrl + "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        for (int i = 0; i < 30 && otherTab.isConnected(); i++) {
            Thread.sleep(100);
        }
        assertThat(otherTab.isConnected()).isFalse();
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
