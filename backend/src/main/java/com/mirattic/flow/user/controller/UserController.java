package com.mirattic.flow.user.controller;

import com.mirattic.flow.auth.service.AuthService;
import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.dto.WithdrawRequest;
import com.mirattic.flow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    /** 필터가 SecurityContext 에 넣어둔 AuthUser 를 그대로 꺼내 쓴다. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return authService.getMe(authUser.id());
    }

    /** 회원 탈퇴. 소셜 회원은 본문 없이 보낼 수 있다. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthUser authUser,
                                         @RequestBody(required = false) WithdrawRequest request) {
        userService.withdraw(authUser.id(), request == null ? null : request.password());
        return ResponseEntity.noContent().build();
    }
}
