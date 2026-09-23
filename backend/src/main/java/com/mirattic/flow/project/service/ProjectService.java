package com.mirattic.flow.project.service;

import com.mirattic.flow.comment.repository.CommentRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.project.dto.ProjectMemberResponse;
import com.mirattic.flow.project.dto.ProjectRequest;
import com.mirattic.flow.project.dto.ProjectResponse;
import com.mirattic.flow.project.dto.ProjectSummaryResponse;
import com.mirattic.flow.project.entity.Project;
import com.mirattic.flow.project.entity.ProjectMember;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.project.repository.ProjectRepository;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.workspace.entity.Workspace;
import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final WorkspaceService workspaceService;
    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;

    // ---------------------------------------------------------------- 권한 검사
    // 워크스페이스와 같은 방식으로 한 곳에 모은다. Phase 5(이슈)·7(채팅)도 requireAccess 를 거친다.

    /**
     * 프로젝트를 볼 수 있는가.
     * 참여자이거나, 워크스페이스 관리자다 — 관리자는 삭제까지 할 수 있으므로 열람을 막을 이유가 없다.
     */
    public Project requireAccess(Long projectId, Long userId) {
        Project project = findProject(projectId);
        if (memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            return project;
        }
        if (!workspaceService.isOwner(project.getWorkspace().getId(), userId)) {
            // 워크스페이스 멤버인지부터 확인해 "존재하지 않는 프로젝트"와 "권한 없음"을 구분하지 않는다.
            throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER);
        }
        return project;
    }

    /** 수정·삭제·참여자 관리. 만든 사람이거나 워크스페이스 관리자. */
    public Project requireManager(Long projectId, Long userId) {
        Project project = requireAccess(projectId, userId);
        if (!canManage(project, userId)) {
            throw new BusinessException(ErrorCode.NOT_PROJECT_MANAGER);
        }
        return project;
    }

    /** 다른 도메인(이슈 등)이 관리 권한을 확인할 때도 쓴다. */
    public boolean canManage(Project project, Long userId) {
        return project.isCreatedBy(userId) || workspaceService.isOwner(project.getWorkspace().getId(), userId);
    }

    // ---------------------------------------------------------------- 프로젝트

    @Transactional
    public ProjectResponse create(Long workspaceId, Long userId, ProjectRequest request) {
        workspaceService.requireMember(workspaceId, userId);

        Workspace workspace = workspaceService.findWorkspace(workspaceId);
        User user = workspaceService.findUser(userId);
        Project project = projectRepository.save(
                Project.create(workspace, request.name(), request.description(), user));

        // 만든 사람은 바로 참여자가 된다. 그래야 이슈 담당자로 지정될 수 있다.
        memberRepository.save(ProjectMember.join(project, user));

        return ProjectResponse.of(project, true);
    }

    public List<ProjectSummaryResponse> findAll(Long workspaceId, Long userId) {
        // 관리자는 워크스페이스 전체를, 일반 멤버는 자기가 참여한 것만 본다.
        WorkspaceMember me = workspaceService.requireMember(workspaceId, userId);
        List<Project> projects = me.isOwner()
                ? projectRepository.findAllByWorkspaceIdWithCreator(workspaceId)
                : projectRepository.findMineByWorkspaceId(workspaceId, userId);

        if (projects.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = countMembers(projects);
        return projects.stream()
                .map(p -> ProjectSummaryResponse.of(p, counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    public ProjectResponse findOne(Long projectId, Long userId) {
        Project project = requireAccess(projectId, userId);
        return ProjectResponse.of(project, canManage(project, userId));
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long userId, ProjectRequest request) {
        Project project = requireManager(projectId, userId);
        project.update(
                request.name(),
                request.description(),
                request.status() != null ? request.status() : project.getStatus());
        return ProjectResponse.of(project, true);
    }

    @Transactional
    public void delete(Long projectId, Long userId) {
        requireManager(projectId, userId);
        // 자식 먼저 — FK 제약
        commentRepository.deleteByProjectId(projectId);
        issueRepository.deleteByProjectId(projectId);
        memberRepository.deleteByProjectId(projectId);
        projectRepository.deleteById(projectId);
    }

    // ---------------------------------------------------------------- 참여자

    public List<ProjectMemberResponse> findMembers(Long projectId, Long userId) {
        requireAccess(projectId, userId);
        return memberRepository.findAllByProjectIdWithUser(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, Long actorId, Long targetUserId) {
        Project project = requireManager(projectId, actorId);

        // 워크스페이스 밖의 사람을 프로젝트에 끌어올 수는 없다.
        if (!workspaceService.isMember(project.getWorkspace().getId(), targetUserId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (memberRepository.existsByProjectIdAndUserId(projectId, targetUserId)) {
            throw new BusinessException(ErrorCode.ALREADY_PROJECT_MEMBER);
        }
        ProjectMember member = memberRepository.save(
                ProjectMember.join(project, workspaceService.findUser(targetUserId)));
        return ProjectMemberResponse.from(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long actorId, Long targetUserId) {
        requireManager(projectId, actorId);
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        memberRepository.delete(member);
        // 만든 사람을 빼도 관리 권한은 createdBy 기준이라 그대로 남는다 — 잠기지 않는다.
    }

    // ---------------------------------------------------------------- 공용

    private Project findProject(Long projectId) {
        return projectRepository.findByIdWithDetails(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    /** 프로젝트마다 count 쿼리를 날리지 않도록 id 를 모아 한 번에 센다. */
    private Map<Long, Long> countMembers(List<Project> projects) {
        List<Long> ids = projects.stream().map(Project::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : memberRepository.countByProjectIds(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
