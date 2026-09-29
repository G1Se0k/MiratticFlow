package com.mirattic.flow.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 Mirattic Auth. JWKS · 토큰 · 폐기 엔드포인트만 흉내 낸다 (JDK 내장 HTTP 서버, 테스트 JVM 당 하나).
 * 실제 Auth 와 같은 규칙: client secret 은 Basic 인증(폼 인코딩 후), PKCE 검증, refresh token 회전.
 */
public final class AuthStub {

    public static final String CLIENT_ID = "mirattic-flow";
    /** + / = 가 들어 있어 Basic 인증 전 폼 인코딩이 필요하다 (application-test.properties 와 같은 값). */
    public static final String CLIENT_SECRET = "test+secret/with=chars";

    private record Login(String sub, String email, String name, String challenge, String redirectUri, Instant authTime) {}

    private static final RSAKey KEY;
    private static final HttpServer SERVER;
    private static final Map<String, Login> CODES = new ConcurrentHashMap<>();
    private record Grant(String sub, Instant authTime) {}
    private static final Map<String, Grant> REFRESH = new ConcurrentHashMap<>(); // token → 로그인
    public static final Set<String> REVOKED = ConcurrentHashMap.newKeySet();
    /** 이 refresh token 을 보내면 Auth 가 invalid_grant 가 아닌 400(invalid_request)으로 답한다. */
    public static final String INVALID_REQUEST = "force-invalid-request";
    /** 이 refresh token 은 폐기에 실패한다 (Auth 장애 흉내). */
    public static final String REVOKE_FAILS = "force-revoke-failure";

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test").generate();
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/oauth2/jwks", ex -> send(ex, 200, new JWKSet(KEY.toPublicJWK()).toString()));
            SERVER.createContext("/oauth2/token", AuthStub::token);
            SERVER.createContext("/oauth2/revoke", ex -> {
                String token = form(ex).get("token");
                if (REVOKE_FAILS.equals(token)) {
                    send(ex, 503, "{}");
                    return;
                }
                REFRESH.remove(token);
                REVOKED.add(token);
                send(ex, authorized(ex) ? 200 : 401, "");
            });
            SERVER.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private AuthStub() {}

    public static String issuer() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** 사용자가 Auth 에서 로그인을 마친 상황: 이 사용자의 code 를 PKCE challenge 에 묶어 발급한다. */
    public static String code(String sub, String email, String name, String challenge, String redirectUri) {
        return code(sub, email, name, challenge, redirectUri, Instant.now());
    }

    /** authTime: 사용자가 Auth 에서 로그인한 시각 (ID token 의 auth_time). */
    public static String code(String sub, String email, String name, String challenge, String redirectUri,
                              Instant authTime) {
        String code = UUID.randomUUID().toString();
        CODES.put(code, new Login(sub, email, name, challenge, redirectUri, authTime));
        return code;
    }

    /** Auth 가 서명한 JWT (iss · iat · exp 에 claims 를 더한다). */
    public static String sign(Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(issuer())
                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 900_000));
            claims.forEach(builder::claim);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                    builder.build());
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 지금 로그인한 것으로 (auth_time = 지금). */
    public static String accessToken(String sub) {
        return accessToken(sub, Instant.now());
    }

    /** authTime: 이 토큰의 로그인 시각 — 실제 Auth 처럼 access token 에도 auth_time 이 있다. */
    public static String accessToken(String sub, Instant authTime) {
        return sign(Map.of("sub", sub, "aud", CLIENT_ID, "scope", List.of("openid", "profile", "email"),
                "auth_time", new Date(authTime.toEpochMilli())));
    }

    /** exp 가 지금부터 seconds 초 뒤인 access token. */
    public static String accessToken(String sub, long seconds) {
        return sign(Map.of("sub", sub, "aud", CLIENT_ID, "scope", List.of("openid"),
                "auth_time", new Date(), "exp", new Date(System.currentTimeMillis() + seconds * 1000)));
    }

    public static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(verifier).getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void token(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            send(ex, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        Map<String, String> f = form(ex);
        String sub;
        Instant authTime;
        Map<String, Object> id = new HashMap<>(Map.of("aud", CLIENT_ID));
        if ("authorization_code".equals(f.get("grant_type"))) {
            Login login = CODES.remove(String.valueOf(f.get("code")));
            if (login == null || !login.redirectUri().equals(f.get("redirect_uri"))
                    || !login.challenge().equals(s256(f.get("code_verifier")))) {
                send(ex, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            sub = login.sub();
            authTime = login.authTime();
            id.put("name", login.name());
            id.put("auth_time", new Date(login.authTime().toEpochMilli()));
            if (login.email() != null) {
                id.put("email", login.email());
            }
        } else {
            if (INVALID_REQUEST.equals(f.get("refresh_token"))) {
                send(ex, 400, "{\"error\":\"invalid_request\"}");
                return;
            }
            Grant grant = REFRESH.remove(String.valueOf(f.get("refresh_token")));
            if (grant == null) {
                send(ex, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            // 재발급해도 원래 로그인의 시각이 이어진다 (실제 Auth 와 같다).
            sub = grant.sub();
            authTime = grant.authTime();
            id.put("auth_time", new Date(authTime.toEpochMilli()));
        }
        id.put("sub", sub);
        String refresh = (UUID.randomUUID() + UUID.randomUUID().toString()).replace("-", "");
        REFRESH.put(refresh, new Grant(sub, authTime));
        send(ex, 200, "{\"access_token\":\"" + accessToken(sub, authTime) + "\",\"refresh_token\":\"" + refresh
                + "\",\"id_token\":\"" + sign(id) + "\",\"token_type\":\"Bearer\",\"expires_in\":899}");
    }

    private static boolean authorized(HttpExchange ex) {
        String basic = CLIENT_ID + ":" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8);
        return ("Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)))
                .equals(ex.getRequestHeaders().getFirst("Authorization"));
    }

    private static Map<String, String> form(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        for (String pair : new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }
}
