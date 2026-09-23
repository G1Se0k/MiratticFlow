package com.mirattic.flow.issue.service;

import com.mirattic.flow.comment.repository.CommentRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.response.PageResponse;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.issue.dto.IssueResponse;
import com.mirattic.flow.issue.dto.IssueSummaryResponse;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueService {

    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final ProjectService projectService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- 조회

    /**
     * 검색·필터·정렬. 빈 문자열은 "조건 없음"으로 본다 —
     * 프론트에서 검색어를 지우면 빈 문자열이 오는데, 그걸 그대로 like 에 넣으면
     * 의미 없는 '%%' 조건이 붙는다.
     */
    public PageResponse<IssueSummaryResponse> search(Long projectId, Long userId, IssueStatus status,
                                                     IssuePriority priority, Long assigneeId, String keyword,
                                                     Pageable pageable) {
        projectService.requireAccess(projectId, userId);
        return PageResponse.of(
                issueRepository.search(projectId, status, priority, assigneeId, blankToNull(keyword), pageable),
                IssueSummaryResponse::from);
    }

    public IssueResponse findOne(Long issueId, Long userId) {
        Issue issue = requireReadable(issueId, userId);
        return IssueResponse.of(issue, canDelete(issue, userId));
    }

    // ---------------------------------------------------------------- 쓰기

    @Transactional
    public IssueResponse create(Long projectId, Long userId, IssueRequest request) {
        Project project = projectService.requireAccess(projectId, userId);

        Issue issue = issueRepository.save(Issue.create(
                project,
                issueRepository.findLastNumber(projectId) + 1,
                request.title(),
                request.description(),
                request.priority() != null ? request.priority() : IssuePriority.MEDIUM,
                findAssignee(projectId, request.assigneeId()),
                findUser(userId),
                request.dueDate()));

        return IssueResponse.of(issue, true);
    }

    /** 수정은 프로젝트 참여자면 누구나 할 수 있다. 협업 도구라 작성자만 고칠 수 있으면 오히려 불편하다. */
    @Transactional
    public IssueResponse update(Long issueId, Long userId, IssueRequest request) {
        Issue issue = requireReadable(issueId, userId);
        issue.update(
                request.title(),
                request.description(),
                request.status() != null ? request.status() : issue.getStatus(),
                request.priority() != null ? request.priority() : issue.getPriority(),
                findAssignee(issue.getProject().getId(), request.assigneeId()),
                request.dueDate());
        return IssueResponse.of(issue, canDelete(issue, userId));
    }

    @Transactional
    public IssueResponse changeStatus(Long issueId, Long userId, IssueStatus status) {
        Issue issue = requireReadable(issueId, userId);
        issue.changeStatus(status);
        return IssueResponse.of(issue, canDelete(issue, userId));
    }

    /** 삭제만 작성자와 프로젝트 관리자로 제한한다. 되돌릴 수 없는 동작이기 때문이다. */
    @Transactional
    public void delete(Long issueId, Long userId) {
        Issue issue = requireReadable(issueId, userId);
        if (!canDelete(issue, userId)) {
            throw new BusinessException(ErrorCode.NOT_ISSUE_OWNER);
        }
        commentRepository.deleteByIssueId(issueId); // 자식 먼저 — FK 제약
        issueRepository.delete(issue);
    }

    // ---------------------------------------------------------------- 공용

    /** 이슈를 볼 수 있는가 = 그 이슈가 속한 프로젝트에 접근할 수 있는가. 댓글도 이 판단을 그대로 쓴다. */
    public Issue requireReadable(Long issueId, Long userId) {
        Issue issue = issueRepository.findByIdWithDetails(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        projectService.requireAccess(issue.getProject().getId(), userId);
        return issue;
    }

    private boolean canDelete(Issue issue, Long userId) {
        return issue.isReportedBy(userId) || projectService.canManage(issue.getProject(), userId);
    }

    /** 담당자는 그 프로젝트의 참여자여야 한다. 아닌 사람을 지정하면 알림도 채팅도 닿지 않는다. */
    private User findAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, assigneeId)) {
            throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER);
        }
        return findUser(assigneeId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
