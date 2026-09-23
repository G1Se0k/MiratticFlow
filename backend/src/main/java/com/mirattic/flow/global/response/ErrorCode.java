package com.mirattic.flow.global.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 에러 응답의 code/message/status 를 한 곳에서 관리한다. */
@Getter
public enum ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다."),
    INVITE_INVALID(HttpStatus.BAD_REQUEST, "사용할 수 없는 초대입니다. 만료되었거나 이미 사용된 초대일 수 있습니다."),
    LAST_OWNER(HttpStatus.BAD_REQUEST, "마지막 관리자는 나가거나 제외될 수 없습니다. 다른 멤버를 관리자로 지정해주세요."),
    CANNOT_CHANGE_OWN_ROLE(HttpStatus.BAD_REQUEST, "자신의 역할은 변경할 수 없습니다."),
    PROJECT_CHAT_FIXED(HttpStatus.BAD_REQUEST, "프로젝트 채팅은 수정하거나 삭제할 수 없습니다."),

    // 401
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해주세요."),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_WORKSPACE_MEMBER(HttpStatus.FORBIDDEN, "워크스페이스 멤버가 아닙니다."),
    NOT_WORKSPACE_OWNER(HttpStatus.FORBIDDEN, "워크스페이스 관리자만 할 수 있습니다."),
    NOT_PROJECT_MEMBER(HttpStatus.FORBIDDEN, "프로젝트 참여자가 아닙니다."),
    NOT_PROJECT_MANAGER(HttpStatus.FORBIDDEN, "프로젝트를 만든 사람이나 워크스페이스 관리자만 할 수 있습니다."),
    NOT_ISSUE_OWNER(HttpStatus.FORBIDDEN, "이슈를 등록한 사람이나 프로젝트 관리자만 삭제할 수 있습니다."),
    NOT_COMMENT_AUTHOR(HttpStatus.FORBIDDEN, "본인이 쓴 댓글만 수정하거나 삭제할 수 있습니다."),
    NOT_TOPIC_MANAGER(HttpStatus.FORBIDDEN, "주제를 만든 사람이나 프로젝트 관리자만 할 수 있습니다."),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 워크스페이스입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "워크스페이스에 없는 멤버입니다."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 프로젝트입니다."),
    ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이슈입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."),
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주제입니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."),

    // 409
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 이메일로 가입된 계정입니다. 이메일로 로그인해주세요."),
    ALREADY_PROJECT_MEMBER(HttpStatus.CONFLICT, "이미 프로젝트에 참여 중인 멤버입니다."),

    // 503
    OAUTH_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "소셜 로그인이 설정되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
