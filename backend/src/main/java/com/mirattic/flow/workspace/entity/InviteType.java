package com.mirattic.flow.workspace.entity;

public enum InviteType {
    /** 특정인에게 보내는 일회용 링크. 만료가 있고 쓰면 소멸한다. */
    LINK,
    /** 워크스페이스마다 하나씩 있는 상시 참여 코드. 재발급하면 이전 것은 무효가 된다. */
    CODE
}
