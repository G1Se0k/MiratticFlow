import { api } from './client';

export type WorkspaceRole = 'OWNER' | 'MEMBER';
export type InviteType = 'LINK' | 'CODE';

export interface Workspace {
  id: number;
  name: string;
  description: string | null;
  myRole: WorkspaceRole;
  createdAt: string;
}

export interface Member {
  userId: number;
  name: string;
  email: string | null; // 소셜 가입자는 이메일이 없을 수 있다
  role: WorkspaceRole;
  joinedAt: string;
}

export interface Invite {
  id: number;
  code: string;
  type: InviteType;
  expiresAt: string | null;
  maxUses: number | null;
  usedCount: number;
  createdAt: string;
}

export interface InvitePreview {
  workspaceId: number;
  workspaceName: string;
  alreadyMember: boolean;
}

export const workspaceApi = {
  list: () => api.get<Workspace[]>('/api/workspaces'),
  get: (id: number) => api.get<Workspace>(`/api/workspaces/${id}`),
  create: (body: { name: string; description?: string }) => api.post<Workspace>('/api/workspaces', body),
  update: (id: number, body: { name: string; description?: string }) =>
    api.patch<Workspace>(`/api/workspaces/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/workspaces/${id}`),

  members: (id: number) => api.get<Member[]>(`/api/workspaces/${id}/members`),
  changeRole: (id: number, userId: number, role: WorkspaceRole) =>
    api.patch<Member>(`/api/workspaces/${id}/members/${userId}`, { role }),
  removeMember: (id: number, userId: number) => api.delete<void>(`/api/workspaces/${id}/members/${userId}`),
  leave: (id: number) => api.delete<void>(`/api/workspaces/${id}/members/me`),

  links: (id: number) => api.get<Invite[]>(`/api/workspaces/${id}/invites`),
  createLink: (id: number) => api.post<Invite>(`/api/workspaces/${id}/invites`),
  revokeLink: (inviteId: number) => api.delete<void>(`/api/invites/${inviteId}`),
  joinCode: (id: number) => api.get<Invite>(`/api/workspaces/${id}/invite-code`),
  regenerateJoinCode: (id: number) => api.post<Invite>(`/api/workspaces/${id}/invite-code`),

  previewInvite: (code: string) => api.get<InvitePreview>(`/api/invites/${code}`),
  acceptInvite: (code: string) => api.post<{ workspaceId: number }>(`/api/invites/${code}/accept`),
};
