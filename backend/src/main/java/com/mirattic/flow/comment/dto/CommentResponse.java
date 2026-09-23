package com.mirattic.flow.comment.dto;

import com.mirattic.flow.comment.entity.Comment;

import java.time.LocalDateTime;

/**
 * canEdit 과 canDelete 가 따로인 이유: 수정은 작성자만, 삭제는 작성자와 프로젝트 관리자가 한다.
 * 하나로 묶으면 관리자 화면에 서버가 거절할 수정 버튼이 나온다.
 */
public record CommentResponse(
        Long id,
        Long authorId,
        String authorName,
        String content,
        boolean canEdit,
        boolean canDelete,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CommentResponse of(Comment comment, boolean canEdit, boolean canDelete) {
        return new CommentResponse(
                comment.getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getContent(),
                canEdit,
                canDelete,
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
