import { api } from './client';

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';

/** 목록용 — 참여자 수만 있고 명단은 없다. */
export interface ProjectSummary {
  id: number;
  name: string;
  description: string | null;
  status: ProjectStatus;
  createdByName: string;
  memberCount: number;
  createdAt: string;
}

export interface Project {
  id: number;
  workspaceId: number;
  name: string;
  description: string | null;
  status: ProjectStatus;
  createdByName: string;
  /** 수정·삭제·참여자 관리 가능 여부. 서버가 판단해 내려준다. */
  canManage: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMember {
  userId: number;
  name: string;
  email: string | null;
  joinedAt: string;
}

export interface ProjectInput {
  name: string;
  description?: string;
  status?: ProjectStatus;
}

export const projectApi = {
  list: (workspaceId: number) => api.get<ProjectSummary[]>(`/api/workspaces/${workspaceId}/projects`),
  create: (workspaceId: number, body: ProjectInput) =>
    api.post<Project>(`/api/workspaces/${workspaceId}/projects`, body),

  get: (id: number) => api.get<Project>(`/api/projects/${id}`),
  update: (id: number, body: ProjectInput) => api.patch<Project>(`/api/projects/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/projects/${id}`),

  members: (id: number) => api.get<ProjectMember[]>(`/api/projects/${id}/members`),
  addMember: (id: number, userId: number) =>
    api.post<ProjectMember>(`/api/projects/${id}/members`, { userId }),
  removeMember: (id: number, userId: number) => api.delete<void>(`/api/projects/${id}/members/${userId}`),
};
