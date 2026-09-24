package com.mirattic.flow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 계정 정보 수정. 보낸 필드만 바뀐다 (null 은 "그대로 둔다"는 뜻).
 *
 * 필수값이 없으므로 @NotBlank 를 붙이지 않는다. 대신 빈 문자열로 이름을 지우는 것은
 * 서비스에서 막는다 — 검증 애너테이션만으로는 "안 보냈다"와 "빈 값을 보냈다"를 구분할 수 없다.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 50, message = "이름은 1자 이상 50자 이하여야 합니다.") String name,
        @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 100) String email,
        String currentPassword,
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.") String newPassword) {

    /** 비밀번호가 바뀌면 다른 기기의 세션까지 끊어야 한다. 서비스와 컨트롤러가 같은 판단을 쓰도록 여기에 둔다. */
    public boolean changesPassword() {
        return newPassword != null;
    }

    public boolean changesEmail() {
        return email != null;
    }
}
