package com.mirattic.flow.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TopicRequest(
        @NotBlank(message = "주제 이름을 입력해주세요.") @Size(max = 50) String name,
        @Size(max = 200) String description) {
}
