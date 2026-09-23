package com.mirattic.flow.chat.entity;

public enum MessageType {
    /** 사람이 보낸 메시지 */
    USER,
    /** 이슈 등록·상태 변경 같은 활동을 알리는 메시지. 보낸 사람이 없다. */
    SYSTEM
}
