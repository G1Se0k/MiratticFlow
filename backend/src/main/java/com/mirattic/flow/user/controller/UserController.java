package com.mirattic.flow.user.controller;

import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.user.dto.UpdateUserRequest;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 필터가 SecurityContext 에 넣어둔 AuthUser 를 그대로 꺼내 쓴다. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return userService.getMe(authUser.id());
    }

    /** 이름 변경. 이메일 · 비밀번호는 Mirattic 계정에서 바꾼다. */
    @PatchMapping("/me")
    public UserResponse update(@AuthenticationPrincipal AuthUser authUser,
                              @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(authUser.id(), request);
    }
}
