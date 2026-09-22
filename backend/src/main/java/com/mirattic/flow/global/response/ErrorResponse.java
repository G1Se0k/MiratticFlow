package com.mirattic.flow.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** 프로젝트 전체가 공유하는 단 하나의 에러 응답 포맷. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(int status, String code, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(), errors);
    }
}
