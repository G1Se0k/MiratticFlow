package com.mirattic.flow.auth.controller;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.OAuthLoginRequest;
import com.mirattic.flow.auth.dto.ReissueRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.auth.dto.TokenResponse;
import com.mirattic.flow.auth.service.AuthService;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.entity.AuthProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** 프론트가 소셜 서비스에서 받아온 인가 코드를 우리 JWT 로 바꿔준다. */
    @PostMapping("/oauth/{provider}")
    public TokenResponse oauthLogin(@PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
        return authService.loginWithOAuth(AuthProvider.fromPath(provider), request.code(), request.state());
    }

    @PostMapping("/reissue")
    public TokenResponse reissue(@Valid @RequestBody ReissueRequest request) {
        return authService.reissue(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody ReissueRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
