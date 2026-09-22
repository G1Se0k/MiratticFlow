package com.mirattic.flow.user.controller;

import com.mirattic.flow.auth.service.AuthService;
import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /** 필터가 SecurityContext 에 넣어둔 AuthUser 를 그대로 꺼내 쓴다. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return authService.getMe(authUser.id());
    }
}
