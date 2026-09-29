package com.mirattic.flow.auth.controller;

import com.mirattic.flow.auth.service.MiratticAuth;
import com.mirattic.flow.global.config.SocketExpiry;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Mirattic 계정 탈퇴 — Auth 가 보내는 삭제 명령 (Auth docs/api.md "Account Deletion").
 * 명령은 Auth 가 서명한 1분짜리 JWT 다: aud = mirattic-flow, sub = 탈퇴하는 Mirattic UID, purpose = account_deletion.
 * purpose 가 있어야만 받는다 — access token 이나 ID token(브라우저도 가진다)으로는 지울 수 없다.
 * 같은 명령이 여러 번 와도 결과가 같다 (없는 사용자는 204). 혼자 관리자인 워크스페이스가 있으면 409 로 거절하고,
 * Auth 는 계정을 지우지 않고 그 이유를 사용자에게 보여준다.
 */
@RestController
public class AccountDeletionController {

    public static final String PATH = "/api/internal/account-deletion";

    private final JwtDecoder orders;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SocketExpiry socketExpiry;

    public AccountDeletionController(MiratticAuth auth, UserRepository userRepository, UserService userService,
                                     SocketExpiry socketExpiry) {
        this.orders = auth.decoder(jwt -> "account_deletion".equals(jwt.getClaimAsString("purpose"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "not a deletion order", null)));
        this.userRepository = userRepository;
        this.userService = userService;
        this.socketExpiry = socketExpiry;
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Jwt order;
        try {
            if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new JwtException("no bearer");
            }
            order = orders.decode(authorization.substring(7));
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<User> user = userRepository.findByMiratticUid(order.getSubject());
        if (user.isEmpty()) {
            return ResponseEntity.noContent().build(); // Flow 를 쓴 적 없거나 이미 지웠다
        }
        Long userId = user.get().getId();
        try {
            userService.withdraw(userId);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.OWNER_WORKSPACE_EXISTS) {
                throw e;
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getErrorCode().getMessage()));
        }
        socketExpiry.closeUser(userId);
        return ResponseEntity.noContent().build();
    }
}
