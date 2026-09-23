package com.mirattic.flow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "내용을 입력해주세요.") @Size(max = 2000) String content) {
}
