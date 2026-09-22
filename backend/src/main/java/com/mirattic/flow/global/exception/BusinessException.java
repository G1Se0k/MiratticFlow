package com.mirattic.flow.global.exception;

import com.mirattic.flow.global.response.ErrorCode;
import lombok.Getter;

/** 비즈니스 규칙 위반. GlobalExceptionHandler 가 ErrorCode 의 status 로 응답한다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
