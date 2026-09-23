package com.mirattic.flow.issue.controller;

import com.mirattic.flow.global.response.PageResponse;
import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.issue.dto.IssueResponse;
import com.mirattic.flow.issue.dto.IssueStatusRequest;
import com.mirattic.flow.issue.dto.IssueSummaryResponse;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
import com.mirattic.flow.issue.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    /** 정렬은 ?sort=dueDate,asc 처럼 Pageable 이 그대로 받는다. 기본은 최신순. */
    @GetMapping("/api/projects/{projectId}/issues")
    public PageResponse<IssueSummaryResponse> search(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return issueService.search(projectId, authUser.id(), status, priority, assigneeId, keyword, pageable);
    }

    @PostMapping("/api/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse create(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long projectId,
                                @Valid @RequestBody IssueRequest request) {
        return issueService.create(projectId, authUser.id(), request);
    }

    @GetMapping("/api/issues/{issueId}")
    public IssueResponse findOne(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long issueId) {
        return issueService.findOne(issueId, authUser.id());
    }

    @PatchMapping("/api/issues/{issueId}")
    public IssueResponse update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long issueId,
                                @Valid @RequestBody IssueRequest request) {
        return issueService.update(issueId, authUser.id(), request);
    }

    @PatchMapping("/api/issues/{issueId}/status")
    public IssueResponse changeStatus(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long issueId,
                                      @Valid @RequestBody IssueStatusRequest request) {
        return issueService.changeStatus(issueId, authUser.id(), request.status());
    }

    @DeleteMapping("/api/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long issueId) {
        issueService.delete(issueId, authUser.id());
    }
}
