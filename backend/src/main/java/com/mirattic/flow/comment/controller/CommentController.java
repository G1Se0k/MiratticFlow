package com.mirattic.flow.comment.controller;

import com.mirattic.flow.comment.dto.CommentRequest;
import com.mirattic.flow.comment.dto.CommentResponse;
import com.mirattic.flow.comment.service.CommentService;
import com.mirattic.flow.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/api/issues/{issueId}/comments")
    public List<CommentResponse> findAll(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long issueId) {
        return commentService.findAll(issueId, authUser.id());
    }

    @PostMapping("/api/issues/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse write(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long issueId,
                                 @Valid @RequestBody CommentRequest request) {
        return commentService.write(issueId, authUser.id(), request);
    }

    @PatchMapping("/api/comments/{commentId}")
    public CommentResponse edit(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long commentId,
                                @Valid @RequestBody CommentRequest request) {
        return commentService.edit(commentId, authUser.id(), request);
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long commentId) {
        commentService.delete(commentId, authUser.id());
    }
}
