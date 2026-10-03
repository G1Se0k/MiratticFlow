package com.mirattic.flow.admin;

import com.mirattic.flow.auth.service.MiratticAuth;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirattic 관리자 콘솔이 읽는 Flow 데이터 (Auth docs/admin.md). Auth 가 서명한 1분짜리 명령으로만 읽는다:
 * aud = mirattic-flow, purpose = admin_read, scope 없음. 읽기 전용이고 수와 워크스페이스 목록만 준다 — 이슈 · 채팅
 * 내용은 주지 않는다. 응답: {"facts": [[이름, 값]], "tables": [{title, columns, rows}]}.
 */
@RestController
public class AdminController {

    public static final String PATH = "/api/internal/admin";

    private static final Duration LIFETIME = Duration.ofMinutes(1);
    private static final Duration SKEW = Duration.ofSeconds(5);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yy.M.d HH:mm");

    private final JwtDecoder orders;
    private final JdbcTemplate jdbc;

    public AdminController(MiratticAuth auth, JdbcTemplate jdbc) {
        this.orders = auth.decoder(jwt -> {
            Instant now = Instant.now();
            Instant iat = jwt.getIssuedAt();
            Instant exp = jwt.getExpiresAt();
            boolean ok = "admin_read".equals(jwt.getClaimAsString("purpose")) && !jwt.hasClaim("scope")
                    && iat != null && exp != null && !exp.isAfter(iat.plus(LIFETIME))
                    && !iat.isAfter(now.plus(SKEW)) && now.isBefore(exp.plus(SKEW));
            return ok ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "not an admin order", null));
        });
        this.jdbc = jdbc;
    }

    @GetMapping(PATH + "/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!authorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LocalDateTime now = LocalDateTime.now();
        List<List<String>> facts = List.of(
                fact("Flow 사용자", count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL")),
                fact("7일 내 가입", count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL "
                        + "AND created_at >= ?", now.minusDays(7))),
                fact("워크스페이스", count("SELECT COUNT(*) FROM workspaces")),
                fact("프로젝트 / 이슈", count("SELECT COUNT(*) FROM projects") + " / " + count("SELECT COUNT(*) FROM issues")),
                fact("24시간 채팅 수", count("SELECT COUNT(*) FROM chat_messages WHERE created_at >= ?", now.minusDays(1))));
        return ResponseEntity.ok(Map.of("facts", facts, "tables", List.of(
                table("최근 워크스페이스", List.of("만듦", "이름", "멤버", "프로젝트"), """
                        SELECT w.created_at, w.name,
                          (SELECT COUNT(*) FROM workspace_members m WHERE m.workspace_id = w.id),
                          (SELECT COUNT(*) FROM projects p WHERE p.workspace_id = w.id)
                        FROM workspaces w ORDER BY w.created_at DESC LIMIT 20"""))));
    }

    @GetMapping(PATH + "/users/{uid}")
    public ResponseEntity<Map<String, Object>> user(@PathVariable String uid,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!authorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Map<String, Object>> found = jdbc.queryForList(
                "SELECT id, name, email, created_at FROM users WHERE mirattic_uid = ?", uid);
        if (found.isEmpty()) {
            return ResponseEntity.ok(Map.of("facts", List.of(fact("Flow 프로필", "없음")), "tables", List.of()));
        }
        Map<String, Object> u = found.getFirst();
        Object id = u.get("id");
        List<List<String>> facts = List.of(
                fact("Flow 이름", String.valueOf(u.get("name"))),
                fact("Flow 이메일", u.get("email") == null ? "-" : String.valueOf(u.get("email"))),
                fact("Flow 가입", time(u.get("created_at"))),
                fact("담당 이슈 (미완료)", count("SELECT COUNT(*) FROM issues WHERE assignee_id = ? AND status <> 'DONE'", id)),
                fact("보낸 채팅", count("SELECT COUNT(*) FROM chat_messages WHERE sender_id = ?", id)));
        return ResponseEntity.ok(Map.of("facts", facts, "tables", List.of(
                table("워크스페이스", List.of("가입", "이름", "역할", "멤버"), """
                        SELECT m.created_at, w.name, m.role,
                          (SELECT COUNT(*) FROM workspace_members o WHERE o.workspace_id = w.id)
                        FROM workspace_members m JOIN workspaces w ON w.id = m.workspace_id
                        WHERE m.user_id = ? ORDER BY m.created_at DESC LIMIT 50""", id))));
    }

    private boolean authorized(String authorization) {
        try {
            return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    && orders.decode(authorization.substring(7)) != null;
        } catch (JwtException e) {
            return false;
        }
    }

    private String count(String sql, Object... args) {
        return String.valueOf(jdbc.queryForObject(sql, Long.class, args));
    }

    private static List<String> fact(String label, String value) {
        return List.of(label, value);
    }

    private static String time(Object value) {
        return value instanceof Timestamp t ? TIME.format(t.toLocalDateTime())
                : value instanceof LocalDateTime l ? TIME.format(l) : "-";
    }

    /** 첫 열은 시각. */
    private Map<String, Object> table(String title, List<String> columns, String sql, Object... args) {
        List<List<String>> rows = jdbc.query(sql, (rs, n) -> {
            List<String> row = new ArrayList<>();
            row.add(time(rs.getTimestamp(1)));
            for (int i = 2; i <= columns.size(); i++) {
                row.add(rs.getString(i));
            }
            return row;
        }, args);
        return Map.of("title", title, "columns", columns, "rows", rows);
    }
}
