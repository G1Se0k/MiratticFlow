package com.mirattic.flow.notification.entity;

/** 알림의 종류. 화면에서 아이콘을 고르는 데 쓴다. 문장 자체는 저장된 content 를 그대로 쓴다. */
public enum NotificationType {
    ISSUE_ASSIGNED,
    ISSUE_STATUS_CHANGED,
    COMMENT_ADDED,
    PROJECT_JOINED
}
