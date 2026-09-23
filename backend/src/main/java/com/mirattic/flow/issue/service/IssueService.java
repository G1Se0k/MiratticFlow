package com.mirattic.flow.issue.service;

import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.chat.repository.TopicRepository;
import com.mirattic.flow.chat.service.SystemMessageSender;
import com.mirattic.flow.comment.repository.CommentRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.global.response.PageResponse;
import com.mirattic.flow.chat.entity.MessageType;
import com.mirattic.flow.issue.dto.ActivityResponse;
import com.mirattic.flow.issue.dto.AssigneeCount;
import com.mirattic.flow.issue.dto.IssueRequest;
import com.mirattic.flow.issue.dto.PriorityCount;
import com.mirattic.flow.issue.dto.ProjectStatsResponse;
import com.mirattic.flow.issue.dto.StatusCount;
import com.mirattic.flow.issue.dto.IssueResponse;
import com.mirattic.flow.issue.dto.IssueSummaryResponse;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.entity.IssuePriority;
import com.mirattic.flow.issue.entity.IssueStatus;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.entity.NotificationType;
import com.mirattic.flow.notification.service.NotificationSender;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueService {

    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final TopicRepository topicRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SystemMessageSender systemMessageSender;
    private final NotificationSender notificationSender;
    private final ProjectService projectService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    /** 최근 활동은 스크롤 없이 훑을 수 있는 만큼만. */
    private static final int ACTIVITY_SIZE = 10;

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

    /**
     * 대시보드 집계. 세는 일은 전부 DB 가 한다 —
     * 이슈를 다 불러와 자바에서 세면 이슈가 늘어날수록 그대로 무너진다.
     */
    public ProjectStatsResponse stats(Long projectId, Long userId) {
        projectService.requireAccess(projectId, userId);

        List<StatusCount> byStatus = fillMissing(
                issueRepository.countByStatus(projectId), IssueStatus.values(),
                StatusCount::status, status -> new StatusCount(status, 0));
        List<PriorityCount> byPriority = fillMissing(
                issueRepository.countByPriority(projectId), IssuePriority.values(),
                PriorityCount::priority, priority -> new PriorityCount(priority, 0));
        List<AssigneeCount> byAssignee = issueRepository.countByAssignee(projectId);

        return new ProjectStatsResponse(
                byStatus.stream().mapToLong(StatusCount::count).sum(),
                countOf(byStatus, IssueStatus.IN_PROGRESS),
                countOf(byStatus, IssueStatus.DONE),
                byAssignee.stream()
                        .filter(assignee -> userId.equals(assignee.userId()))
                        .mapToLong(AssigneeCount::count).findFirst().orElse(0),
                byStatus,
                byPriority,
                byAssignee,
                chatMessageRepository.findRecentActivity(projectId, MessageType.SYSTEM, Limit.of(ACTIVITY_SIZE)));
    }

    /**
     * 0건인 값은 group by 결과에 아예 없다. 그대로 그리면 "완료 0건"일 때 조각이 사라져
     * 차트 모양이 매번 달라진다. 빠진 것을 0으로 채워 항상 같은 축을 갖게 한다.
     */
    private static <T, E> List<T> fillMissing(List<T> counted, E[] all,
                                              Function<T, E> keyOf, Function<E, T> zero) {
        Map<E, T> found = counted.stream().collect(Collectors.toMap(keyOf, Function.identity()));
        return Arrays.stream(all).map(value -> found.getOrDefault(value, zero.apply(value))).toList();
    }

    private static long countOf(List<StatusCount> counts, IssueStatus status) {
        return counts.stream().filter(count -> count.status() == status)
                .mapToLong(StatusCount::count).findFirst().orElse(0);
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

        systemMessageSender.send(projectId, "%s님이 %s를 등록했습니다.".formatted(issue.getReporter().getName(), label(issue)));
        if (issue.getAssignee() != null) {
            notifyAssigned(issue, userId);
        }
        return IssueResponse.of(issue, true);
    }

    /** 수정은 프로젝트 참여자면 누구나 할 수 있다. 협업 도구라 작성자만 고칠 수 있으면 오히려 불편하다. */
    @Transactional
    public IssueResponse update(Long issueId, Long userId, IssueRequest request) {
        Issue issue = requireReadable(issueId, userId);
        IssueStatus before = issue.getStatus();
        User previousAssignee = issue.getAssignee();

        issue.update(
                request.title(),
                request.description(),
                request.status() != null ? request.status() : issue.getStatus(),
                request.priority() != null ? request.priority() : issue.getPriority(),
                findAssignee(issue.getProject().getId(), request.assigneeId()),
                request.dueDate());

        // 제목·설명이 바뀐 것까지 채팅에 흘리면 대화가 묻힌다. 팀이 알아야 할 두 가지만 알린다.
        String actor = findUser(userId).getName();
        if (before != issue.getStatus()) {
            announceStatus(issue, actor);
            notifyStatusChanged(issue, userId);
        }
        if (!sameUser(previousAssignee, issue.getAssignee())) {
            announceAssignee(issue, actor);
            notifyAssigned(issue, userId);
        }
        return IssueResponse.of(issue, canDelete(issue, userId));
    }

    @Transactional
    public IssueResponse changeStatus(Long issueId, Long userId, IssueStatus status) {
        Issue issue = requireReadable(issueId, userId);
        if (issue.getStatus() != status) {
            issue.changeStatus(status);
            announceStatus(issue, findUser(userId).getName());
            notifyStatusChanged(issue, userId);
        }
        return IssueResponse.of(issue, canDelete(issue, userId));
    }

    /** 삭제만 작성자와 프로젝트 관리자로 제한한다. 되돌릴 수 없는 동작이기 때문이다. */
    @Transactional
    public void delete(Long issueId, Long userId) {
        Issue issue = requireReadable(issueId, userId);
        if (!canDelete(issue, userId)) {
            throw new BusinessException(ErrorCode.NOT_ISSUE_OWNER);
        }
        // 자식 먼저 — FK 제약. 파생 삭제는 커밋 시점에 순서가 정해지므로 벌크 쿼리로 직접 보낸다.
        chatMessageRepository.deleteByIssueId(issueId);
        topicRepository.deleteByIssueId(issueId);
        commentRepository.deleteByIssueId(issueId);
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

    // ---------------------------------------------------------------- 시스템 메시지

    /** "ISSUE-12" — 사용자에게 보이는 이슈 식별자. */
    private static String label(Issue issue) {
        return "ISSUE-" + issue.getNumber();
    }

    private void announceStatus(Issue issue, String actor) {
        systemMessageSender.send(issue.getProject().getId(),
                "%s님이 %s 상태를 %s로 변경했습니다.".formatted(actor, label(issue), issue.getStatus()));
    }

    private void announceAssignee(Issue issue, String actor) {
        String assignee = issue.getAssignee() != null
                ? issue.getAssignee().getName() + "님으로 지정했습니다."
                : "없음으로 바꿨습니다.";
        systemMessageSender.send(issue.getProject().getId(),
                "%s님이 %s 담당자를 %s".formatted(actor, label(issue), assignee));
    }

    // ---------------------------------------------------------------- 알림

    /** 담당자가 빠지는 경우(null)에는 알릴 사람이 없다. */
    private void notifyAssigned(Issue issue, Long actorId) {
        if (issue.getAssignee() == null) {
            return;
        }
        notificationSender.send(actorId, NotificationType.ISSUE_ASSIGNED,
                "%s 담당자로 지정되었습니다: %s".formatted(label(issue), issue.getTitle()),
                link(issue), issue.getAssignee());
    }

    /** 담당자와 작성자 둘 다 알아야 한다. 같은 사람이거나 본인이 바꿨으면 Sender 가 걸러낸다. */
    private void notifyStatusChanged(Issue issue, Long actorId) {
        notificationSender.send(actorId, NotificationType.ISSUE_STATUS_CHANGED,
                "%s 상태가 %s로 바뀌었습니다.".formatted(label(issue), issue.getStatus()),
                link(issue), issue.getAssignee(), issue.getReporter());
    }

    private static String link(Issue issue) {
        return "/issues/" + issue.getId();
    }

    private static boolean sameUser(User a, User b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getId().equals(b.getId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
