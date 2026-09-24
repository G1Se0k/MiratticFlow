package com.mirattic.flow.user.controller;

import com.mirattic.flow.auth.service.AuthService;
import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.user.dto.UpdateUserRequest;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.dto.WithdrawRequest;
import com.mirattic.flow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /** 계정 정보 수정. 이름 · 이메일 · 비밀번호를 한 번에 보낼 수도, 하나만 보낼 수도 있다. */
    @PatchMapping("/me")
    public UserResponse update(@AuthenticationPrincipal AuthUser authUser,
                              @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(authUser.id(), request);
    }

    /** 회원 탈퇴. 소셜 회원은 본문 없이 보낼 수 있다. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthUser authUser,
                                         @RequestBody(required = false) WithdrawRequest request) {
        userService.withdraw(authUser.id(), request == null ? null : request.password());
        return ResponseEntity.noContent().build();
    }
}
