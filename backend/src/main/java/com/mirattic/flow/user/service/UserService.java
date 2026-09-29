package com.mirattic.flow.user.service;

import com.mirattic.flow.chat.entity.ChatMessage;
import com.mirattic.flow.chat.repository.ChatMessageRepository;
import com.mirattic.flow.global.exception.BusinessException;
import com.mirattic.flow.global.response.ErrorCode;
import com.mirattic.flow.issue.repository.IssueRepository;
import com.mirattic.flow.notification.repository.NotificationRepository;
import com.mirattic.flow.project.repository.ProjectMemberRepository;
import com.mirattic.flow.user.dto.UpdateUserRequest;
import com.mirattic.flow.user.dto.UserResponse;
import com.mirattic.flow.user.entity.User;
import com.mirattic.flow.user.entity.Withdrawal;
import com.mirattic.flow.user.repository.UserRepository;
import com.mirattic.flow.user.repository.WithdrawalRepository;
import com.mirattic.flow.workspace.entity.WorkspaceMember;
import com.mirattic.flow.workspace.entity.WorkspaceRole;
import com.mirattic.flow.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IssueRepository issueRepository;
    private final WithdrawalRepository withdrawalRepository;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)));
    }

    /**
     * Mirattic 계정으로 로그인했을 때 그 계정의 Flow 사용자를 찾고, 처음이면 만든다.
     * 이메일 사본은 매번 Auth 의 값으로 갱신한다 (Auth 에서 바뀌었을 수 있다). 이름은 Flow 에서 바꾼 값을 지킨다.
     */
    @Transactional
    public Long signIn(String miratticUid, String email, String name, Instant authTime) {
        // 같은 계정의 로그인과 탈퇴(Auth 의 삭제 명령 포함)는 차례로 — 삭제가 끝난 뒤에 끝나는 로그인이 사용자를 다시
        // 만들지 못하게. 잠근 뒤 처음 읽으므로 방금 커밋된 탈퇴까지 본다.
        Withdrawal fence = lockUid(miratticUid);
        return userRepository.findByMiratticUid(miratticUid)
                .map(user -> {
                    user.updateEmail(email);
                    return user.getId();
                })
                .orElseGet(() -> {
                    // 탈퇴했던 계정이면 탈퇴보다 뒤의 로그인이어야 새 사용자를 만든다 (Withdrawal).
                    if (authTime.getEpochSecond() <= fence.getWithdrawnAt()) {
                        throw new FreshLoginRequired();
                    }
                    return userRepository.save(User.create(miratticUid, email, name, authTime.getEpochSecond())).getId();
                });
    }

    /**
     * Mirattic 계정 탈퇴 (Auth 의 삭제 명령). Flow 사용자가 없어도 탈퇴 경계를 지금으로 남긴다: 삭제 전에 받아 둔
     * 로그인(더 이른 auth_time)이 뒤늦게 콜백에 닿아도 사용자를 만들지 못한다. 반환: 탈퇴시킨 Flow 사용자 id.
     * 혼자 관리자인 워크스페이스가 있으면 BusinessException(OWNER_WORKSPACE_EXISTS) — 아무것도 바뀌지 않는다.
     */
    @Transactional
    public Optional<Long> deleteByAuth(String miratticUid) {
        Withdrawal fence = lockUid(miratticUid);
        Optional<User> user = userRepository.findByMiratticUid(miratticUid);
        if (user.isEmpty()) {
            fence.withdrawnAgain(Instant.now().getEpochSecond());
            return Optional.empty();
        }
        withdraw(user.get().getId());
        return Optional.of(user.get().getId());
    }

    /** 이 계정의 탈퇴 기록 행을 잠근다 (없으면 경계 0 으로 만들어서). 계정별 잠금으로 쓴다. */
    private Withdrawal lockUid(String miratticUid) {
        String hash = hash(miratticUid);
        withdrawalRepository.ensureRow(hash);
        return withdrawalRepository.lock(hash);
    }

    /**
     * Auth 의 access token 이 가리키는 Flow 사용자 id. REST · WebSocket · 로그아웃 · 탈퇴 확인이 모두 이것을 쓴다.
     * sub 로 찾되, 그 사용자가 생기기 전의 로그인(auth_time)이면 받지 않는다 — 탈퇴 후 다시 가입한 계정에
     * 탈퇴 전의 로그인이 통하지 않게.
     */
    public Optional<Long> resolve(Jwt accessToken) {
        Instant authTime = accessToken.getClaimAsInstant("auth_time");
        if (authTime == null) {
            return Optional.empty();
        }
        return userRepository.findByMiratticUid(accessToken.getSubject())
                .filter(user -> user.acceptsLoginAt(authTime.getEpochSecond()))
                .map(User::getId);
    }

    /** 이름 변경. 이메일 · 비밀번호는 Mirattic 계정의 것이라 여기서 바꾸지 않는다. */
    @Transactional
    public UserResponse update(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // @Size(min = 1) 은 공백만 있는 이름을 통과시킨다. 화면에 빈 자리로 보이는 이름은 없는 것과 같다.
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        user.changeName(name);
        return UserResponse.from(user);
    }

    /**
     * 회원 탈퇴 (Flow 의 데이터만. Mirattic 계정은 Auth 에 남는다).
     *
     * 이 사용자를 참조하는 이슈 · 댓글 · 채팅이 남아 있어 users 행 자체는 지울 수 없다.
     * 지운다면 탈퇴한 사람의 기록만이 아니라 함께 일한 팀의 기록까지 사라진다.
     * 그래서 행은 남기고 개인정보 컬럼을 비우며(User.withdraw), 본인에게만 속한 것들은 실제로 지운다.
     * 본인 확인은 호출하는 쪽(LoginController)이 한다: Auth 에서 방금 비밀번호를 다시 입력한 같은 계정일 때만 부른다.
     */
    /** 탈퇴를 막는 조건(혼자 관리자인 워크스페이스)이 없는가. 탈퇴 확인 로그인을 시작하기 전에 먼저 본다. */
    public boolean canWithdraw(Long userId) {
        try {
            requireNoSoleOwnedWorkspace(userId, false);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            return;
        }

        // 잠금 순서: 계정(탈퇴 기록 행) → 사용자 행 → 관리자 자리. Auth 의 삭제 명령(deleteByAuth)과 Flow 의 탈퇴가
        // 같은 순서로 잡아 서로 막히지 않는다. 기다리는 사이 다른 쪽이 탈퇴시켰으면 할 일이 없다.
        Withdrawal fence = lockUid(user.getMiratticUid());
        if (userRepository.lockIfNotWithdrawn(userId).isEmpty()) {
            return;
        }
        requireNoSoleOwnedWorkspace(userId, true);

        // 다시 가입할 때의 기준 (탈퇴보다 뒤의 로그인만) — miratticUid 를 비우기 전에 남긴다.
        fence.withdrawnAgain(Instant.now().getEpochSecond());

        // 개인정보 파기가 먼저다.
        // 아래 벌크 쿼리들은 clearAutomatically = true 라 실행 후 영속성 컨텍스트를 비운다.
        // 그 뒤에 엔티티를 고치면 이미 준영속 상태여서 변경 감지가 일어나지 않고,
        // 멤버십만 지워진 채 개인정보가 그대로 남는다 (204 를 받고도 파기되지 않는다).
        user.withdraw();
        userRepository.saveAndFlush(user);

        // 다른 사람의 기록 문구(채팅 활동 줄, 댓글 알림)에 남은 이름도 "탈퇴한 사용자"로 바꾼다. 그 사람 자리로 기록된
        // 행에서 그 자리(위치와 길이)만 바꾸므로, 그사이 이름을 바꿨어도 · 같은 이름의 다른 사람이 있어도 정확하다.
        String shown = user.getName();
        chatMessageRepository.replaceLeadName(userId, shown);
        chatMessageRepository.replaceAssigneeName(userId, shown, ChatMessage.ASSIGNED_TAIL);
        notificationRepository.replaceLeadName(userId, shown);

        issueRepository.unassignByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        projectMemberRepository.deleteByUserId(userId);
        workspaceMemberRepository.deleteByUserId(userId);
    }

    private static String hash(String miratticUid) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(miratticUid.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 관리자가 나 하나뿐인 워크스페이스를 남기고 떠날 수는 없다.
     * 워크스페이스를 나갈 때(leave)와 같은 규칙이다 — 주인이 없으면 아무도 그 워크스페이스를 손댈 수 없다.
     */
    private void requireNoSoleOwnedWorkspace(Long userId, boolean lock) {
        List<Long> owned = workspaceMemberRepository.findAllByUserIdAndRole(userId, WorkspaceRole.OWNER).stream()
                .map(m -> m.getWorkspace().getId()).sorted().toList(); // 같은 순서로 잠가 교착을 피한다
        for (Long workspaceId : owned) {
            // 탈퇴는 잠그고 센다 (WorkspaceMemberRepository.lockAllByWorkspaceIdAndRole). 미리 보기(canWithdraw)는 그냥 센다.
            long owners = lock
                    ? workspaceMemberRepository.lockAllByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER).size()
                    : workspaceMemberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
            if (owners <= 1) {
                throw new BusinessException(ErrorCode.OWNER_WORKSPACE_EXISTS);
            }
        }
    }
}
