package com.mirattic.flow.project.dto;

import com.mirattic.flow.project.entity.ProjectMember;

import java.time.LocalDateTime;

public record ProjectMemberResponse(Long userId, String name, String email, LocalDateTime joinedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(), // 소셜 가입자는 null 일 수 있다
                member.getCreatedAt());
    }
}
