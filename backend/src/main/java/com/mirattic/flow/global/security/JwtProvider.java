package com.mirattic.flow.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/** JWT 생성 / 검증 / 파싱만 담당. 비즈니스 로직은 모른다. */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-ms}") long accessTokenValidityMs,
            @Value("${app.jwt.refresh-token-validity-ms}") long refreshTokenValidityMs) {
        // HS256 은 최소 256bit 키를 요구한다. 짧은 secret 을 넣으면 여기서 바로 실패한다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, accessTokenValidityMs);
    }

    /**
     * Refresh Token 에는 jti(고유 식별자)를 넣는다.
     * 넣지 않으면 같은 사용자가 같은 초에 재발급받을 때 payload 가 완전히 같아져
     * 새 토큰 문자열이 이전 것과 바이트 단위로 동일해진다 → rotation 이 무의미해지고
     * 폐기된 토큰의 재사용도 탐지할 수 없다.
     */
    public String createRefreshToken(Long userId, String email) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenValidityMs))
                .signWith(key)
                .compact();
    }

    public LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now().plusNanos(refreshTokenValidityMs * 1_000_000);
    }

    private String createToken(Long userId, String email, long validityMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    /** 서명과 만료를 함께 검증한다. 실패하면 예외를 던져 호출자가 401 로 바꾼다. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public String getEmail(String token) {
        return parse(token).get("email", String.class);
    }

    static LocalDateTime toLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
