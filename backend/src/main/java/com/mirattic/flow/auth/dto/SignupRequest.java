package com.mirattic.flow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotBlank @Size(max = 50) String name) {
}
