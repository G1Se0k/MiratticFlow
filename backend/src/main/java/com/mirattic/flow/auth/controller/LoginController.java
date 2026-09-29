package com.mirattic.flow.auth.controller;

import com.mirattic.flow.auth.service.AuthCookies;
import com.mirattic.flow.auth.service.MiratticAuth;
import com.mirattic.flow.global.config.SocketExpiry;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.user.service.FreshLoginRequired;
import com.mirattic.flow.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirattic 계정 로그인 (Authorization Code + PKCE, Flow 백엔드가 기밀 클라이언트).
 *
 * <pre>
 * /auth/start    브라우저 → state · PKCE 를 만들어 Auth /oauth2/authorize 로 보낸다
 * /auth/callback Auth 가 code 를 들고 돌려보낸다 → client secret 으로 토큰 교환 → HttpOnly 쿠키 → 원래 화면
 * </pre>
 * state 는 flow_login 쿠키로 이 브라우저에 묶는다 — 남이 시작한 로그인을 내 브라우저에서 끝내게 만드는
 * 공격(login CSRF)을 막는다. 토큰은 URL 에 절대 싣지 않는다.
 */
@Slf4j
@Controller
public class LoginController {

    /**
     * reauth: 이 로그인이 prompt=login 으로 시작했는가 (로그아웃 · 탈퇴 표시가 있던 브라우저, 또는 탈퇴 확인).
     * withdrawUserId: 탈퇴 확인이면 그 Flow 사용자. startedAt: 시작 시각 — 탈퇴 확인의 로그인은 이보다 뒤여야 한다.
     */
    private record Pending(String verifier, String next, long expiresAt, boolean reauth, Long withdrawUserId,
                           long startedAt) {}

    public static final String STATE_COOKIE = "flow_login";
    private static final Duration LOGIN_TTL = Duration.ofMinutes(10);
    private static final int MAX_PENDING = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MiratticAuth auth;
    private final AuthCookies cookies;
    private final UserService userService;
    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final SocketExpiry socketExpiry;
    // ponytail: 메모리 보관이라 서버가 재시작되면 진행 중인 로그인은 다시 시작해야 한다. 단일 인스턴스 전제.
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public LoginController(MiratticAuth auth, AuthCookies cookies, UserService userService, JwtDecoder jwtDecoder,
                           UserRepository userRepository, SocketExpiry socketExpiry) {
        this.auth = auth;
        this.cookies = cookies;
        this.userService = userService;
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
        this.socketExpiry = socketExpiry;
    }

    /**
     * 로그인 시작. withdraw=true 면 회원 탈퇴 확인이다: 지금 로그인한 사용자가 Auth 에서 한 번 더 로그인해야
     * (prompt=login) 콜백에서 탈퇴가 실행된다. 쿠키의 토큰만으로는 탈퇴할 수 없다 (자리를 비운 PC, 탈취한 세션).
     */
    @GetMapping("/auth/start")
    public String start(@RequestParam(required = false) String next,
                        @RequestParam(defaultValue = "false") boolean withdraw,
                        @RequestParam(defaultValue = "false") boolean fresh,
                        @CookieValue(name = AuthCookies.REAUTH, required = false) String reauth,
                        @CookieValue(name = AuthCookies.ACCESS, required = false) String accessToken,
                        HttpServletResponse response) {
        long now = System.currentTimeMillis();
        Long withdrawUserId = null;
        if (withdraw) {
            withdrawUserId = currentUser(accessToken);
            if (withdrawUserId == null) {
                return "redirect:/account?withdraw=expired";
            }
            if (!userService.canWithdraw(withdrawUserId)) {
                return "redirect:/account?withdraw=blocked";
            }
        }
        if (pending.size() >= MAX_PENDING) {
            pending.values().removeIf(p -> p.expiresAt() <= now);
            if (pending.size() >= MAX_PENDING) {
                return "redirect:/login?error=busy";
            }
        }
        String state = random();
        String verifier = random();
        boolean promptLogin = reauth != null || withdraw || fresh;
        pending.put(state, new Pending(verifier, safeNext(next), now + LOGIN_TTL.toMillis(), promptLogin,
                withdrawUserId, now));
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(state, LOGIN_TTL));
        UriComponentsBuilder authorize = UriComponentsBuilder.fromUriString(auth.issuer() + "/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", auth.clientId())
                .queryParam("redirect_uri", auth.redirectUri())
                .queryParam("scope", "openid profile email")
                .queryParam("state", state)
                .queryParam("code_challenge", s256(verifier))
                .queryParam("code_challenge_method", "S256");
        if (promptLogin) {
            // 로그아웃 · 탈퇴한 뒤의 첫 로그인, 또는 탈퇴 확인: Auth 세션이 남아 있어도 비밀번호를 다시 묻게 한다.
            authorize.queryParam("prompt", "login");
        }
        return "redirect:" + authorize.encode().build().toUriString();
    }

    @GetMapping("/auth/callback")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           @CookieValue(name = STATE_COOKIE, required = false) String stateCookie,
                           @CookieValue(name = AuthCookies.REAUTH, required = false) String reauth,
                           HttpServletResponse response) {
        Pending p = state == null ? null : pending.remove(state);
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO));
        if (p == null || p.expiresAt() <= System.currentTimeMillis()) {
            return "redirect:/login?error=expired";
        }
        if (!state.equals(stateCookie) || error != null || code == null) {
            return "redirect:/login?error=failed";
        }
        if (reauth != null && !p.reauth()) {
            // 로그아웃 · 탈퇴 전에 (다른 탭에서) 시작한 로그인이 뒤늦게 끝났다. 비밀번호를 다시 묻지 않은 로그인이므로 받지 않는다.
            return "redirect:/login?error=expired";
        }
        MiratticAuth.TokenResponse tokens;
        Jwt id;
        try {
            tokens = auth.exchange(code, p.verifier());
            id = auth.idTokenDecoder().decode(tokens.id_token());
        } catch (RuntimeException e) {
            log.warn("Mirattic Auth 로그인 실패: {}", e.getClass().getSimpleName());
            return p.withdrawUserId() != null ? "redirect:/account?withdraw=failed" : "redirect:/login?error=failed";
        }
        if (p.withdrawUserId() != null) {
            return withdraw(p, tokens, id, response);
        }
        try {
            signIn(id);
        } catch (FreshLoginRequired e) {
            // 탈퇴했던 계정의 예전 Auth 로그인 세션으로 다시 가입하려 한다: 비밀번호를 다시 입력하게 하고 처음부터.
            auth.revokeQuietly(tokens.refresh_token());
            return p.reauth() ? "redirect:/login?error=failed"
                    : "redirect:/auth/start?fresh=true&next=" + UriUtils.encodeQueryParam(p.next(), StandardCharsets.UTF_8);
        }
        try {
            cookies.set(response, tokens);
            if (p.reauth()) {
                cookies.reauthDone(response); // Auth 가 비밀번호를 다시 물은 로그인이다.
            }
        } catch (RuntimeException e) {
            log.warn("Mirattic Auth 로그인 실패: {}", e.getClass().getSimpleName());
            return "redirect:/login?error=failed";
        }
        return "redirect:" + p.next();
    }

    /**
     * 탈퇴 확인 로그인의 콜백. 같은 계정이 방금(시작 뒤에) 비밀번호를 다시 입력했을 때만 탈퇴한다.
     * 그 뒤 이 기기의 로그인을 모두 끝낸다: 방금 받은 토큰 폐기, 쿠키 삭제, 다음 로그인 재확인 표시,
     * 그리고 Auth 의 로그인 세션(SSO) 종료 — 브라우저가 스스로 Auth 로 보내는 폼(POST)을 응답한다.
     */
    private String withdraw(Pending p, MiratticAuth.TokenResponse tokens, Jwt id, HttpServletResponse response) {
        Long userId = userRepository.findByMiratticUid(id.getSubject()).map(u -> u.getId()).orElse(null);
        Instant authTime = id.getClaimAsInstant("auth_time");
        // auth_time 은 초 단위라 시작 시각을 초로 내려 비교한다.
        boolean fresh = authTime != null && authTime.getEpochSecond() >= p.startedAt() / 1000;
        if (!p.withdrawUserId().equals(userId) || !fresh) {
            auth.revokeQuietly(tokens.refresh_token()); // 탈퇴 확인용으로만 받은 로그인이다
            return "redirect:/account?withdraw=mismatch";
        }
        try {
            userService.withdraw(userId);
        } catch (BusinessException e) {
            auth.revokeQuietly(tokens.refresh_token());
            return "redirect:/account?withdraw=blocked";
        }
        socketExpiry.closeUser(userId);
        auth.revokeQuietly(tokens.refresh_token());
        cookies.clear(response);
        cookies.requireReauth(response);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            response.getWriter().write(endSessionPage(tokens.id_token()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    /** Auth 의 /connect/logout 으로 스스로 제출되는 폼. GET 이면 ID token 이 주소 · 방문 기록에 남는다. */
    private String endSessionPage(String idToken) {
        return """
                <!doctype html><html lang="ko"><meta charset="utf-8"><title>탈퇴 처리 중</title>
                <body onload="document.forms[0].submit()">
                <form method="post" action="%s">
                <input type="hidden" name="id_token_hint" value="%s">
                <input type="hidden" name="post_logout_redirect_uri" value="%s">
                <input type="hidden" name="state" value="withdraw">
                <noscript><button type="submit">계속</button></noscript>
                </form></body></html>
                """.formatted(HtmlUtils.htmlEscape(auth.issuer() + "/connect/logout"), HtmlUtils.htmlEscape(idToken),
                HtmlUtils.htmlEscape(auth.postLogoutRedirectUri()));
    }

    /** 쿠키의 access token 이 가리키는 Flow 사용자. 없거나 만료면 null. */
    private Long currentUser(String accessToken) {
        if (accessToken == null) {
            return null;
        }
        try {
            return userService.resolve(jwtDecoder.decode(accessToken)).orElse(null);
        } catch (JwtException e) {
            return null;
        }
    }

    /** 같은 계정의 첫 로그인이 두 탭에서 동시에 끝나면 한쪽의 INSERT 가 유니크 충돌 — 다시 읽으면 다른 쪽이 만든 사용자가 있다. */
    private void signIn(Jwt id) {
        String email = id.getClaimAsString("email");
        String displayName = name(id.getClaimAsString("name"));
        // 새 사용자의 기준 시각: 이 로그인의 auth_time. Auth 는 늘 넣는다 — 없으면 가장 오래된 것으로 친다
        // (탈퇴했던 계정이면 다시 로그인하게 된다).
        Instant authTime = id.hasClaim("auth_time") ? id.getClaimAsInstant("auth_time") : Instant.EPOCH;
        try {
            userService.signIn(id.getSubject(), email, displayName, authTime);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            // 다른 탭의 첫 로그인과 겹쳤거나(유니크 충돌), 그사이 탈퇴 등으로 행이 바뀌었다(버전 충돌).
            // 새로 읽어 다시 한다 — 탈퇴했다면 이번엔 탈퇴한 뒤의 상태를 보고 판단한다.
            userService.signIn(id.getSubject(), email, displayName, authTime);
        }
    }

    /** 로그인 뒤 돌아갈 곳. 우리 사이트 안의 경로만 (//evil.com 같은 다른 사이트로 보내는 열린 리다이렉트 방지). */
    static String safeNext(String next) {
        return next != null && next.startsWith("/") && !next.startsWith("//") && !next.contains("\\")
                && next.length() <= 512 ? next : "/";
    }

    private static String name(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return "사용자";
        }
        return n.length() > 50 ? n.substring(0, 50) : n;
    }

    private String stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value).httpOnly(true).secure(cookies.secure()).sameSite("Lax")
                .path("/auth/callback").maxAge(maxAge).build().toString();
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String s256(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
