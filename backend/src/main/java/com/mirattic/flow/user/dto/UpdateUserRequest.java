package com.mirattic.flow.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 이름 변경. 이메일과 비밀번호는 Mirattic 계정(auth.mirattic.com)에서 바꾼다.
 *
 * 공백만 있는 이름과 길이 제한(한글 10자 · 영어 20자, Mirattic 계정과 같은 규칙)은 서비스에서 본다.
 */
public record UpdateUserRequest(
        @NotNull String name) {
}
