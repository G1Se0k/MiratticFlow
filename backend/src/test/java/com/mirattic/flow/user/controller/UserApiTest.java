package com.mirattic.flow.user.controller;

import com.mirattic.flow.auth.controller.AccountDeletionController;
import com.mirattic.flow.auth.service.AuthCookies;
import com.mirattic.flow.support.ApiTestSupport;
import com.mirattic.flow.support.AuthStub;
import com.mirattic.flow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴는 DB 까지 가봐야 확인되는 동작이다.
 * 단위 테스트는 리포지토리가 대역이라 영속성 컨텍스트가 없고, 실제로 지워졌는지 알 수 없다.
 * 탈퇴는 Auth 에서 비밀번호를 다시 입력하는 흐름(/auth/start?withdraw=true)으로만 된다.
 */
class UserApiTest extends ApiTestSupport {

    @Autowired private UserRepository userRepository;

    private record Signed(String uid, String token) {}

    private Signed user(String name) throws Exception {
        String uid = UUID.randomUUID().toString();
        return new Signed(uid, signIn(uid, uid.substring(0, 8) + "@test.com", name).getCookie(AuthCookies.ACCESS).getValue());
    }

    @Test
    @DisplayName("탈퇴: 같은 계정이 Auth 에서 방금 다시 로그인하면 DB 의 개인정보가 비워지고, 이 기기의 로그인이 모두 끝난다")
    void withdrawErasesPersonalData() throws Exception {
        Signed me = user("탈퇴할사람");
        long userId = userIdOf(me.token());

        MockHttpServletResponse done = withdraw(me.token(), me.uid(), Instant.now());

        // 204 만 보고 넘어가면 "응답은 성공인데 지워지지 않는" 경우를 놓친다.
        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getMiratticUid()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.isWithdrawn()).isTrue();
        // Auth 의 로그인 세션을 끝내는 폼이 스스로 제출된다 (ID token 은 주소가 아니라 POST 본문으로).
        assertThat(done.getContentAsString()).contains(AuthStub.issuer() + "/connect/logout")
                .contains("name=\"id_token_hint\"").contains("value=\"withdraw\"");
        assertThat(done.getCookie(AuthCookies.ACCESS).getMaxAge()).isZero();
        assertThat(done.getCookie(AuthCookies.REAUTH).getValue()).isEqualTo("1");
        // 탈퇴 전에 받은 토큰은 더 이상 이 사용자를 가리키지 않는다.
        authed(get("/api/users/me"), me.token()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰만으로는 탈퇴할 수 없다 — 예전의 DELETE /api/users/me 는 없다")
    void noWithdrawalWithoutReauthentication() throws Exception {
        Signed me = user("그대로");
        authed(delete("/api/users/me"), me.token()).andExpect(status().is4xxClientError());
        authed(get("/api/users/me"), me.token()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Auth 에서 다른 계정으로 로그인했거나, 탈퇴를 시작하기 전의 로그인이면 탈퇴하지 않는다")
    void withdrawNeedsTheSameAccountAndAFreshSignIn() throws Exception {
        Signed me = user("나");
        assertThat(withdraw(me.token(), UUID.randomUUID().toString(), Instant.now()).getRedirectedUrl())
                .isEqualTo("/account?withdraw=mismatch");
        assertThat(withdraw(me.token(), me.uid(), Instant.now().minusSeconds(120)).getRedirectedUrl())
                .isEqualTo("/account?withdraw=mismatch");
        authed(get("/api/users/me"), me.token()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 쿠키가 없으면 탈퇴 확인을 시작하지 않는다")
    void withdrawNeedsASignedInBrowser() throws Exception {
        assertThat(withdraw(null, UUID.randomUUID().toString(), Instant.now()).getRedirectedUrl())
                .isEqualTo("/account?withdraw=expired");
    }

    @Test
    @DisplayName("탈퇴한 뒤 같은 Mirattic 계정으로 다시 로그인하면 새 사용자로 시작한다")
    void withdrawnAccountStartsOver() throws Exception {
        Signed first = user("처음");
        long firstId = userIdOf(first.token());
        withdraw(first.token(), first.uid(), Instant.now());

        // 탈퇴보다 뒤의 새 로그인으로 다시 온다.
        var again = callback(mockMvc.perform(get("/auth/start")).andReturn().getResponse(), first.uid(),
                Instant.now().plusSeconds(5)).getCookie(AuthCookies.ACCESS);
        long secondId = id(bodyOf(mockMvc.perform(get("/api/users/me").cookie(again))));
        assertThat(secondId).isNotEqualTo(firstId);
    }

    @Test
    @DisplayName("탈퇴 전의 로그인(다른 브라우저의 토큰)은 다시 가입한 새 사용자로 통하지 않는다 — 재발급해도")
    void loginsFromBeforeWithdrawalDoNotReachTheRejoinedUser() throws Exception {
        String uid = UUID.randomUUID().toString();
        MockHttpServletResponse otherBrowser = signIn(uid, null, "다른 브라우저");
        String oldAccess = otherBrowser.getCookie(AuthCookies.ACCESS).getValue();
        jakarta.servlet.http.Cookie oldRefresh = otherBrowser.getCookie(AuthCookies.REFRESH);

        String here = signIn(uid, null, "여기").getCookie(AuthCookies.ACCESS).getValue();
        withdraw(here, uid, Instant.now());
        // 다시 가입: 새 로그인 (auth_time 이 탈퇴 전의 로그인들보다 뒤)
        var start = mockMvc.perform(get("/auth/start")).andReturn().getResponse();
        var query = org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getRedirectedUrl()).build()
                .getQueryParams();
        String code = AuthStub.code(uid, null, "다시", java.net.URLDecoder.decode(query.getFirst("code_challenge"),
                java.nio.charset.StandardCharsets.UTF_8), java.net.URLDecoder.decode(query.getFirst("redirect_uri"),
                java.nio.charset.StandardCharsets.UTF_8), Instant.now().plusSeconds(5));
        String rejoined = mockMvc.perform(get("/auth/callback").param("code", code)
                        .param("state", java.net.URLDecoder.decode(query.getFirst("state"), java.nio.charset.StandardCharsets.UTF_8))
                        .cookie(start.getCookie("flow_login")))
                .andReturn().getResponse().getCookie(AuthCookies.ACCESS).getValue();
        authed(get("/api/users/me"), rejoined).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("다시"));

        // 탈퇴 전 로그인의 access token: 거절
        authed(get("/api/users/me"), oldAccess).andExpect(status().isUnauthorized());
        // 그 로그인의 refresh token 으로 재발급받아도 (Auth 는 그 로그인의 auth_time 을 그대로 준다): 거절
        var renewed = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/refresh").cookie(oldRefresh)
                        .header(com.mirattic.flow.global.security.CsrfHeaderFilter.HEADER,
                                com.mirattic.flow.global.security.CsrfHeaderFilter.VALUE))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        mockMvc.perform(get("/api/users/me").cookie(renewed.getCookie(AuthCookies.ACCESS)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 브라우저에 남은 탈퇴 전의 Auth 로그인 세션으로는 다시 가입할 수 없다 — 비밀번호를 다시 묻는다")
    void rejoiningThroughAnOldSsoSessionAsksForTheLoginAgain() throws Exception {
        String uid = UUID.randomUUID().toString();
        String oldAccess = signIn(uid, null, "예전").getCookie(AuthCookies.ACCESS).getValue();
        String here = signIn(uid, null, "여기").getCookie(AuthCookies.ACCESS).getValue();
        withdraw(here, uid, Instant.now());

        // The other browser: no re-auth marker, its Auth session is from before the withdrawal.
        var start = mockMvc.perform(get("/auth/start")).andReturn().getResponse();
        assertThat(start.getRedirectedUrl()).doesNotContain("prompt=");
        var cb = callback(start, uid, Instant.now().minusSeconds(60));
        assertThat(cb.getRedirectedUrl()).startsWith("/auth/start?fresh=true");
        assertThat(cb.getCookie(AuthCookies.ACCESS)).isNull();

        // The restart asks Auth for the password again; that login may rejoin.
        var restart = mockMvc.perform(get(cb.getRedirectedUrl())).andReturn().getResponse();
        assertThat(restart.getRedirectedUrl()).contains("prompt=login");
        String rejoined = callback(restart, uid, Instant.now().plusSeconds(5)).getCookie(AuthCookies.ACCESS).getValue();
        authed(get("/api/users/me"), rejoined).andExpect(status().isOk());
        authed(get("/api/users/me"), oldAccess).andExpect(status().isUnauthorized());
    }

    private MockHttpServletResponse callback(MockHttpServletResponse start, String uid, Instant authTime) throws Exception {
        var query = org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getRedirectedUrl()).build()
                .getQueryParams();
        java.util.function.Function<String, String> d = v -> java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8);
        String code = AuthStub.code(uid, null, "다시", d.apply(query.getFirst("code_challenge")),
                d.apply(query.getFirst("redirect_uri")), authTime);
        return mockMvc.perform(get("/auth/callback").param("code", code).param("state", d.apply(query.getFirst("state")))
                .cookie(start.getCookie("flow_login"))).andReturn().getResponse();
    }

    @Test
    @DisplayName("혼자 관리자인 워크스페이스가 있으면 탈퇴 확인을 시작하지 않는다")
    void withdrawBlockedBySoleOwnedWorkspace() throws Exception {
        Signed me = user("관리자");
        createWorkspace(me.token());

        assertThat(withdraw(me.token(), me.uid(), Instant.now()).getRedirectedUrl())
                .isEqualTo("/account?withdraw=blocked");
        // 막혔으면 아무것도 지워지지 않아야 한다.
        authed(get("/api/users/me"), me.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("관리자"));
    }

    private static String deletionOrder(String uid, String audience, String purpose) {
        return AuthStub.sign(purpose == null ? Map.of("sub", uid, "aud", audience)
                : Map.of("sub", uid, "aud", audience, "purpose", purpose));
    }

    private org.springframework.test.web.servlet.ResultActions deletionOrder(String token) throws Exception {
        var request = post(AccountDeletionController.PATH);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    @Test
    @DisplayName("Mirattic 계정 탈퇴: Auth 의 삭제 명령으로 개인정보를 비우고, 같은 명령이 다시 와도 204 다")
    void authDeletionOrderErasesTheUser() throws Exception {
        Signed me = user("계정탈퇴");
        long userId = userIdOf(me.token());

        deletionOrder(deletionOrder(me.uid(), AuthStub.CLIENT_ID, "account_deletion")).andExpect(status().isNoContent());
        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getMiratticUid()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.isWithdrawn()).isTrue();
        authed(get("/api/users/me"), me.token()).andExpect(status().isUnauthorized());

        deletionOrder(deletionOrder(me.uid(), AuthStub.CLIENT_ID, "account_deletion")).andExpect(status().isNoContent());
        // Flow 를 쓴 적 없는 계정도 204.
        deletionOrder(deletionOrder(UUID.randomUUID().toString(), AuthStub.CLIENT_ID, "account_deletion"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("삭제 명령만 받는다 — access token · ID token · 다른 서비스의 명령 · 서명 없는 요청은 401")
    void onlyAuthsDeletionOrderIsAccepted() throws Exception {
        Signed me = user("남아있음");
        deletionOrder(me.token()).andExpect(status().isUnauthorized());                       // access token
        deletionOrder(deletionOrder(me.uid(), AuthStub.CLIENT_ID, null)).andExpect(status().isUnauthorized()); // ID token 모양
        deletionOrder(deletionOrder(me.uid(), "mirattic-sync", "account_deletion")).andExpect(status().isUnauthorized());
        deletionOrder("not-a-jwt").andExpect(status().isUnauthorized());
        deletionOrder((String) null).andExpect(status().isUnauthorized());
        authed(get("/api/users/me"), me.token()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("혼자 관리자인 워크스페이스가 있으면 삭제 명령을 409 와 이유로 거절하고 아무것도 지우지 않는다")
    void deletionOrderRefusedForASoleOwner() throws Exception {
        Signed me = user("관리자");
        createWorkspace(me.token());
        deletionOrder(deletionOrder(me.uid(), AuthStub.CLIENT_ID, "account_deletion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("혼자 관리자인 워크스페이스")));
        authed(get("/api/users/me"), me.token()).andExpect(status().isOk());
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 워크스페이스에 들여보내고 프로젝트 참여자로 넣는다 ("{이름}님이 참여했습니다."). */
    private void join(String ownerToken, long projectId, Signed who) throws Exception {
        authed(post("/api/invites/{code}/accept", inviteCode(ownerToken, workspaceIdOfProject(projectId, ownerToken))),
                who.token()).andExpect(status().isOk());
        authed(post("/api/projects/{id}/members", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new com.mirattic.flow.project.dto.AddMemberRequest(userIdOf(who.token())))), ownerToken)
                .andExpect(status().isCreated());
    }

    private long issue(String token, long projectId, String title) throws Exception {
        return id(bodyOf(authed(post("/api/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new com.mirattic.flow.issue.dto.IssueRequest(title, null, null, null, null, null))), token)));
    }

    private void assign(String token, long issueId, long assigneeId) throws Exception {
        authed(patch("/api/issues/{id}", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new com.mirattic.flow.issue.dto.IssueRequest("이슈", null, null, null, assigneeId, null))), token)
                .andExpect(status().isOk());
    }

    private void comment(String token, long issueId) throws Exception {
        authed(post("/api/issues/{id}/comments", issueId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new com.mirattic.flow.comment.dto.CommentRequest("확인했습니다"))), token)
                .andExpect(status().isCreated());
    }

    private List<String> texts(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }

    @Test
    @DisplayName("탈퇴하면 다른 사람의 기록 문구 속 이름도 '탈퇴한 사용자'가 된다 — 같은 이름이 들어간 다른 사람 기록은 그대로")
    void withdrawalReplacesTheNameInOthersRecords() throws Exception {
        String owner = newUserToken("오너");
        long projectId = setUpProject(owner);
        Signed leaving = user("철수");
        Signed other = user("김철수"); // 이름이 "철수"로 끝나는 다른 사람
        join(owner, projectId, leaving);
        join(owner, projectId, other);
        long ownersIssue = issue(owner, projectId, "오너 이슈");
        issue(leaving.token(), projectId, "철수 이슈");
        assign(owner, ownersIssue, userIdOf(leaving.token()));
        long othersIssue = issue(owner, projectId, "다른 이슈");
        assign(owner, othersIssue, userIdOf(other.token()));
        comment(leaving.token(), ownersIssue);
        comment(other.token(), ownersIssue);
        long leavingId = userIdOf(leaving.token());
        long otherId = userIdOf(other.token());
        // 탈퇴 전에 이름을 바꿔도 예전 이름이 들어간 문구까지 바뀐다 (이름이 아니라 자리로 바꾼다).
        authed(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "영희🙂"))), leaving.token()).andExpect(status().isOk());

        deletionOrder(deletionOrder(leaving.uid(), AuthStub.CLIENT_ID, "account_deletion")).andExpect(status().isNoContent());

        String mine = "select content from chat_messages where lead_user_id = ? or assignee_user_id = ? order by id";
        assertThat(texts(mine, leavingId, leavingId)).containsExactly(
                "탈퇴한 사용자님이 참여했습니다.",
                "탈퇴한 사용자님이 ISSUE-2를 등록했습니다.",
                "오너님이 ISSUE-1 담당자를 탈퇴한 사용자님으로 지정했습니다.");
        assertThat(texts(mine, otherId, otherId)).containsExactly(
                "김철수님이 참여했습니다.",
                "오너님이 ISSUE-3 담당자를 김철수님으로 지정했습니다.");
        String sent = "select content from notifications where actor_id = ? and type = 'COMMENT_ADDED'";
        assertThat(texts(sent, leavingId)).containsExactly("탈퇴한 사용자님이 ISSUE-1에 댓글을 남겼습니다.");
        assertThat(texts(sent, otherId)).allMatch(t -> t.startsWith("김철수님이 ISSUE-1에 댓글"));
        assertThat(texts("select content from chat_messages where content like '%철수%' and (lead_user_id = ? or assignee_user_id = ?)",
                leavingId, leavingId)).isEmpty();
    }

    @Test
    @DisplayName("Flow 를 쓴 적 없는 계정의 삭제 명령도 경계를 남긴다 — 삭제 전에 받아 둔 로그인이 뒤늦게 와도 사용자를 만들지 못한다")
    void aLoginIssuedBeforeTheDeletionCannotCreateTheUserAfterwards() throws Exception {
        String uid = UUID.randomUUID().toString();
        Instant issuedBefore = Instant.now().minusSeconds(5);
        deletionOrder(deletionOrder(uid, AuthStub.CLIENT_ID, "account_deletion")).andExpect(status().isNoContent());

        MockHttpServletResponse late = signIn(uid, "late@test.com", "늦은로그인", null, issuedBefore);
        assertThat(late.getCookie(AuthCookies.ACCESS)).isNull();
        assertThat(userRepository.findByMiratticUid(uid)).isEmpty();
    }

    @Test
    @DisplayName("같은 삭제 명령이 동시에 두 번 와도 둘 다 204 (계정별로 차례로 처리한다)")
    void concurrentCopiesOfAnOrderBothSucceed() throws Exception {
        for (int round = 0; round < 5; round++) {
            Signed me = user("동시" + round);
            String order = deletionOrder(me.uid(), AuthStub.CLIENT_ID, "account_deletion");
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> sent = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                sent.add(pool.submit(() -> {
                    go.await();
                    return deletionOrder(order).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            for (var f : sent) {
                assertThat(f.get()).as("round " + round).isEqualTo(204);
            }
            pool.shutdown();
            assertThat(userRepository.findByMiratticUid(me.uid())).isEmpty();
        }
    }

    @Autowired private com.mirattic.flow.user.service.UserService userService;

    @Test
    @DisplayName("Flow 의 탈퇴와 Auth 의 삭제 명령이 동시에 와도 (관리자를 나눠 가진 사람) 막히지 않고 한 번만 탈퇴한다")
    void flowWithdrawalAndAuthOrderAtOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            Signed me = user("둘다" + round);
            String partner = newUserToken();
            long workspaceId = createWorkspace(me.token());
            authed(post("/api/invites/{code}/accept", inviteCode(me.token(), workspaceId)), partner).andExpect(status().isOk());
            authed(patch("/api/workspaces/{id}/members/{userId}", workspaceId, userIdOf(partner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new com.mirattic.flow.workspace.dto.RoleRequest(
                            com.mirattic.flow.workspace.entity.WorkspaceRole.OWNER))), me.token())
                    .andExpect(status().isOk());
            long userId = userIdOf(me.token());
            String order = deletionOrder(me.uid(), AuthStub.CLIENT_ID, "account_deletion");

            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            var direct = pool.submit(() -> {
                go.await();
                userService.withdraw(userId);
                return null;
            });
            var byAuth = pool.submit(() -> {
                go.await();
                return deletionOrder(order).andReturn().getResponse().getStatus();
            });
            go.countDown();
            direct.get();
            assertThat(byAuth.get()).as("round " + round).isEqualTo(204);
            pool.shutdown();
            assertThat(userRepository.findById(userId).orElseThrow().isWithdrawn()).isTrue();
        }
    }

    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    @DisplayName("탈퇴가 진행 중이면 그 사람의 댓글은 기다렸다가 거절된다 — 탈퇴 뒤에 예전 이름이 든 알림이 새로 생기지 않는다")
    void aCommentRacingTheWithdrawalWaitsAndIsRefused() throws Exception {
        String owner = newUserToken("오너");
        long projectId = setUpProject(owner);
        Signed leaving = user("곧탈퇴");
        join(owner, projectId, leaving);
        long issueId = issue(owner, projectId, "이슈");
        long leavingId = userIdOf(leaving.token());

        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        // The withdrawal holds the person's row (as UserService.withdraw does) ...
        var withdrawal = pool.submit(() -> tx.executeWithoutResult(s -> {
            userRepository.lockIfNotWithdrawn(leavingId);
            locked.countDown();
            try {
                finish.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            userService.withdraw(leavingId);
        }));
        locked.await();
        // ... so a comment by that person waits instead of writing the old name.
        var commenting = pool.submit(() -> authed(post("/api/issues/{id}/comments", issueId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new com.mirattic.flow.comment.dto.CommentRequest("막차"))), leaving.token())
                .andReturn().getResponse().getStatus());
        Thread.sleep(500);
        assertThat(commenting.isDone()).as("the comment waits for the withdrawal").isFalse();
        finish.countDown();
        withdrawal.get();
        assertThat(commenting.get()).isGreaterThanOrEqualTo(400);
        pool.shutdown();
        assertThat(texts("select content from notifications where actor_id = ?", leavingId)).isEmpty();
    }

    @Test
    @DisplayName("이름을 바꿀 수 있다")
    void changeName() throws Exception {
        String token = newUserToken("원래이름");

        authed(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "바뀐이름"))), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("바뀐이름"));
    }

    @Test
    @DisplayName("공백뿐인 이름은 거절된다")
    void blankNameRejected() throws Exception {
        String token = newUserToken();

        authed(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "   "))), token)
                .andExpect(status().isBadRequest());
    }
}
