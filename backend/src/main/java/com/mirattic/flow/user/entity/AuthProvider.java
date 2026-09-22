package com.mirattic.flow.user.entity;

import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;

import java.util.Arrays;

public enum AuthProvider {
    /** 이메일 + 비밀번호로 직접 가입한 사용자 */
    LOCAL,
    KAKAO,
    NAVER;

    /** URL 경로의 "kakao" 같은 값을 enum 으로 바꾼다. 모르는 값이면 400 으로 응답한다. */
    public static AuthProvider fromPath(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider != LOCAL && provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER));
    }
}
