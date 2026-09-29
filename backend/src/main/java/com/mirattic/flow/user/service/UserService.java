package com.mirattic.flow.user.service;

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
        return userRepository.findByMiratticUid(miratticUid)
                .map(user -> {
                    user.updateEmail(email);
                    return user.getId();
                })
                .orElseGet(() -> {
                    // 탈퇴했던 계정이면 탈퇴보다 뒤의 로그인이어야 새 사용자를 만든다 (Withdrawal).
                    withdrawalRepository.findById(hash(miratticUid)).ifPresent(w -> {
                        if (authTime.getEpochSecond() <= w.getWithdrawnAt()) {
                            throw new FreshLoginRequired();
                        }
                    });
                    return userRepository.save(User.create(miratticUid, email, name, authTime.getEpochSecond())).getId();
                });
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
            requireNoSoleOwnedWorkspace(userId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        requireNoSoleOwnedWorkspace(userId);

        // 다시 가입할 때의 기준 (탈퇴보다 뒤의 로그인만) — miratticUid 를 비우기 전에 남긴다.
        long now = Instant.now().getEpochSecond();
        String uidHash = hash(user.getMiratticUid());
        withdrawalRepository.findById(uidHash).ifPresentOrElse(w -> w.withdrawnAgain(now),
                () -> withdrawalRepository.save(new Withdrawal(uidHash, now)));

        // 개인정보 파기가 먼저다.
        // 아래 벌크 쿼리들은 clearAutomatically = true 라 실행 후 영속성 컨텍스트를 비운다.
        // 그 뒤에 엔티티를 고치면 이미 준영속 상태여서 변경 감지가 일어나지 않고,
        // 멤버십만 지워진 채 개인정보가 그대로 남는다 (204 를 받고도 파기되지 않는다).
        user.withdraw();
        userRepository.saveAndFlush(user);

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
    private void requireNoSoleOwnedWorkspace(Long userId) {
        List<WorkspaceMember> owned = workspaceMemberRepository.findAllByUserIdAndRole(userId, WorkspaceRole.OWNER);
        for (WorkspaceMember member : owned) {
            long owners = workspaceMemberRepository.countByWorkspaceIdAndRole(
                    member.getWorkspace().getId(), WorkspaceRole.OWNER);
            if (owners <= 1) {
                throw new BusinessException(ErrorCode.OWNER_WORKSPACE_EXISTS);
            }
        }
    }
}
