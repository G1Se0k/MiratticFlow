package com.mirattic.flow.chat.controller;

import com.mirattic.flow.chat.dto.ChatMessageResponse;
import com.mirattic.flow.chat.dto.TopicRequest;
import com.mirattic.flow.chat.dto.TopicResponse;
import com.mirattic.flow.chat.service.ChatService;
import com.mirattic.flow.chat.service.TopicService;
import com.mirattic.flow.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;
    private final ChatService chatService;

    @GetMapping("/api/projects/{projectId}/topics")
    public List<TopicResponse> findAll(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId) {
        return topicService.findAll(projectId, authUser.id());
    }

    @PostMapping("/api/projects/{projectId}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicResponse create(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId,
                                @Valid @RequestBody TopicRequest request) {
        return topicService.create(projectId, authUser.id(), request);
    }

    @PatchMapping("/api/topics/{topicId}")
    public TopicResponse update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long topicId,
                                @Valid @RequestBody TopicRequest request) {
        return topicService.update(topicId, authUser.id(), request);
    }

    @DeleteMapping("/api/topics/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long topicId) {
        topicService.delete(topicId, authUser.id());
    }

    /** 과거 메시지. before 보다 id 가 작은 것들을 최신순으로 가져온다. */
    @GetMapping("/api/topics/{topicId}/messages")
    public List<ChatMessageResponse> messages(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long topicId,
                                              @RequestParam(required = false) Long before,
                                              @RequestParam(defaultValue = "50") int size) {
        return chatService.history(topicId, authUser.id(), before, size);
    }
}
