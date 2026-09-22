package com.mirattic.flow.workspace.dto;

/**
 * 초대 링크를 열었을 때 "어디에 들어가는지" 보여주기 위한 최소 정보.
 * 아직 멤버가 아닌 사람에게 나가는 응답이므로 워크스페이스 이름 외에는 담지 않는다.
 */
public record InvitePreviewResponse(Long workspaceId, String workspaceName, boolean alreadyMember) {
}
