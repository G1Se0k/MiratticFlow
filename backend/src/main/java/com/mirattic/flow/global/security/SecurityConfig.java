package com.mirattic.flow.global.security;

import com.mirattic.flow.admin.AdminController;
import com.mirattic.flow.auth.controller.AccountDeletionController;
import com.mirattic.flow.auth.service.AuthCookies;
import com.mirattic.flow.auth.service.MiratticAuth;
import com.mirattic.flow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 인증은 Mirattic Auth 의 access token 으로 한다 (RS256, Auth JWKS 로 서명 검증, iss · aud · exp 확인).
 * 브라우저는 HttpOnly 쿠키(flow_at)로, 테스트와 도구는 Authorization: Bearer 헤더로 보낸다.
 * 토큰의 sub(Mirattic UID)로 Flow 사용자를 찾아 AuthUser 로 만든다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityExceptionHandlers securityExceptionHandlers;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserService userService) throws Exception {
        return http
                // Spring 의 CSRF 토큰 대신 커스텀 헤더 검사(CsrfHeaderFilter)를 쓴다. 이유는 그 클래스에.
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new CsrfHeaderFilter(securityExceptionHandlers.accessDeniedHandler()), CsrfFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 요청마다 토큰으로 자신을 증명한다. 서버는 세션을 만들지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // 로그인 시작 · 콜백, 재발급 · 로그아웃은 access token 없이 온다 (쿠키의 refresh token 으로).
                        .requestMatchers("/auth/**", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // WebSocket 핸드셰이크는 통과시킨다. 인증은 STOMP CONNECT 에서 StompAuthInterceptor 가 한다.
                        .requestMatchers("/ws/**").permitAll()
                        // Auth 의 탈퇴 명령. access token 이 아니라 컨트롤러가 명령 JWT 를 직접 검증한다.
                        .requestMatchers(AccountDeletionController.PATH).permitAll()
                        // 관리자 콘솔의 읽기 명령도 마찬가지 (AdminController 가 검증).
                        .requestMatchers(AdminController.PATH + "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(tokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            // 탈퇴했거나(miratticUid 가 비워짐), 로그인 콜백을 거치지 않았거나, 이 사용자가 생기기 전의
                            // 로그인이면 Flow 사용자가 아니다 (UserService.resolve).
                            Long userId = userService.resolve(token)
                                    .orElseThrow(() -> new InvalidBearerTokenException("unknown Flow user"));
                            return new UsernamePasswordAuthenticationToken(new AuthUser(userId), token, List.of());
                        }))
                        .authenticationEntryPoint(securityExceptionHandlers.entryPoint()))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(securityExceptionHandlers.entryPoint())
                        .accessDeniedHandler(securityExceptionHandlers.accessDeniedHandler()))
                .build();
    }

    /** Auth access token 만 받는다. ID token 도 같은 키 · iss · aud 라서 scope 로 구분한다. */
    @Bean
    public JwtDecoder jwtDecoder(MiratticAuth auth) {
        return auth.decoder(jwt -> jwt.hasClaim("scope")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "not an access token", null)));
    }

    /**
     * Authorization 헤더, 없으면 flow_at 쿠키.
     * 재발급 · 로그아웃 · 로그인 경로에서는 읽지 않는다 — 만료된 access token 이 함께 와도 거기서 401 이 나면 안 된다.
     */
    private BearerTokenResolver tokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            if (path.startsWith("/api/auth/") || path.startsWith("/auth/") || path.startsWith("/ws")
                    || path.equals(AccountDeletionController.PATH) || path.startsWith(AdminController.PATH + "/")) {
                return null;
            }
            String token = header.resolve(request);
            return token != null ? token : AuthCookies.read(request, AuthCookies.ACCESS);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 쿠키 인증이라 자격 증명을 허용한다 (허용 출처는 위 목록으로 한정).
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
