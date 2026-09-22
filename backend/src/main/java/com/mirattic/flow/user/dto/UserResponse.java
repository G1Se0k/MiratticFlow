package com.mirattic.flow.user.dto;

import com.mirattic.flow.user.entity.User;

import java.time.LocalDateTime;

/** 엔티티는 컨트롤러 밖으로 내보내지 않는다 (비밀번호 노출 방지). */
public record UserResponse(Long id, String email, String name, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
