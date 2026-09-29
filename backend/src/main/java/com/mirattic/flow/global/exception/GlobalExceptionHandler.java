package com.mirattic.flow.global.exception;

import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** 컨트롤러에서 빠져나온 예외를 한 곳에서 응답 포맷으로 변환한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    /** @Valid 검증 실패 → 어떤 필드가 왜 틀렸는지까지 내려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
    }

    /** 없는 경로 · 허용하지 않는 메서드 같은 스프링의 4xx 는 그 상태 그대로 (전부 500 으로 내리면 서버 장애처럼 보인다). */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleSpringClientError(Exception e) {
        int status = ((org.springframework.web.ErrorResponse) e).getStatusCode().value();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status, "INVALID_REQUEST", "지원하지 않는 요청입니다.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.", List.of()));
    }
}
