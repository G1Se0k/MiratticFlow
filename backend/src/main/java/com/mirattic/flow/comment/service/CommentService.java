package com.mirattic.flow.comment.service;

import com.mirattic.flow.comment.dto.CommentRequest;
import com.mirattic.flow.comment.dto.CommentResponse;
import com.mirattic.flow.comment.entity.Comment;
import com.mirattic.flow.comment.repository.CommentRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.entity.Issue;
import com.mirattic.flow.issue.service.IssueService;
import com.mirattic.flow.project.service.ProjectService;
import com.mirattic.flow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueService issueService;
    private final ProjectService projectService;
    private final UserRepository userRepository;

    /** 댓글을 볼 수 있는가 = 그 이슈를 볼 수 있는가. 권한 판단을 이슈에 그대로 맡긴다. */
    public List<CommentResponse> findAll(Long issueId, Long userId) {
        issueService.requireReadable(issueId, userId);
        return commentRepository.findAllByIssueIdWithAuthor(issueId).stream()
                .map(comment -> CommentResponse.of(comment, comment.isWrittenBy(userId), canDelete(comment, userId)))
                .toList();
    }

    @Transactional
    public CommentResponse write(Long issueId, Long userId, CommentRequest request) {
        Issue issue = issueService.requireReadable(issueId, userId);
        Comment comment = commentRepository.save(Comment.write(
                issue,
                userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)),
                request.content()));
        return CommentResponse.of(comment, true, true);
    }

    /** 수정은 작성자만 한다. 남의 말을 고치는 것은 이슈 내용을 고치는 것과 다르다. */
    @Transactional
    public CommentResponse edit(Long commentId, Long userId, CommentRequest request) {
        Comment comment = requireReadable(commentId, userId);
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
        }
        comment.edit(request.content());
        return CommentResponse.of(comment, true, canDelete(comment, userId));
    }

    /** 삭제는 작성자와 프로젝트 관리자가 할 수 있다. 관리자에게는 부적절한 글을 치울 방법이 필요하다. */
    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = requireReadable(commentId, userId);
        if (!canDelete(comment, userId)) {
            throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
        }
        commentRepository.delete(comment);
    }

    private Comment requireReadable(Long commentId, Long userId) {
        Comment comment = commentRepository.findByIdWithDetails(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        issueService.requireReadable(comment.getIssue().getId(), userId);
        return comment;
    }

    private boolean canDelete(Comment comment, Long userId) {
        return comment.isWrittenBy(userId) || projectService.canManage(comment.getIssue().getProject(), userId);
    }
}
