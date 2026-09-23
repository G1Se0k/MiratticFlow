import { api } from './client';

export interface Topic {
  id: number;
  projectId: number;
  /** null 이면 프로젝트 채팅. */
  issueId: number | null;
  name: string;
  description: string | null;
  createdByName: string;
  /** 이름 변경·삭제 가능 여부. 만든 사람이거나 프로젝트 관리자일 때 참이다. */
  canManage: boolean;
  createdAt: string;
}

export type MessageType = 'USER' | 'SYSTEM';

export interface ChatMessage {
  id: number;
  topicId: number;
  senderId: number | null;
  senderName: string | null;
  content: string;
  type: MessageType;
  createdAt: string;
}

export const chatApi = {
  /** 프로젝트 채팅은 프로젝트마다 하나 고정이라 단건이다. */
  projectChat: (projectId: number) => api.get<Topic>(`/api/projects/${projectId}/chat`),

  topics: (issueId: number) => api.get<Topic[]>(`/api/issues/${issueId}/topics`),
  createTopic: (issueId: number, body: { name: string; description?: string }) =>
    api.post<Topic>(`/api/issues/${issueId}/topics`, body),
  updateTopic: (id: number, body: { name: string; description?: string }) =>
    api.patch<Topic>(`/api/topics/${id}`, body),
  removeTopic: (id: number) => api.delete<void>(`/api/topics/${id}`),

  /** 과거 메시지. before 보다 이전 것들을 가져온다(커서). */
  messages: (topicId: number, before?: number) =>
    api.get<ChatMessage[]>(`/api/topics/${topicId}/messages${before ? `?before=${before}` : ''}`),
};
