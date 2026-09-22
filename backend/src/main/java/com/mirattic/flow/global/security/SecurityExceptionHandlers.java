package com.mirattic.flow.global.security;

import tools.jackson.databind.ObjectMapper;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 단계에서 터지는 인증/인가 실패는 컨트롤러에 도달하지 않으므로
 * GlobalExceptionHandler 가 잡지 못한다. 여기서 같은 포맷으로 직접 써준다.
 */
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandlers {

    private final ObjectMapper objectMapper;

    /** 인증 안 됨 → 401 */
    public AuthenticationEntryPoint entryPoint() {
        return (request, response, authException) -> write(response, ErrorCode.UNAUTHORIZED);
    }

    /** 인증은 됐지만 권한 없음 → 403 */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> write(response, ErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
