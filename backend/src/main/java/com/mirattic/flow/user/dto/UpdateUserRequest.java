package com.mirattic.flow.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 이름 변경. 이메일과 비밀번호는 Mirattic 계정(auth.mirattic.com)에서 바꾼다.
 *
 * 공백만 있는 이름은 @Size 를 통과하므로 서비스에서 한 번 더 막는다.
 */
public record UpdateUserRequest(
        @NotNull @Size(min = 1, max = 50, message = "이름은 1자 이상 50자 이하여야 합니다.") String name) {
}
