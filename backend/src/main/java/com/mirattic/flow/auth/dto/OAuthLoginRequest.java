package com.mirattic.flow.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** state 는 네이버 토큰 교환에 필요하다. CSRF 대조 자체는 프론트에서 이미 끝난 상태다. */
public record OAuthLoginRequest(@NotBlank String code, String state) {
}
