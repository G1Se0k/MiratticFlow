import { api } from './client';

export interface Comment {
  id: number;
  authorId: number;
  authorName: string;
  content: string;
  /** 수정은 작성자만, 삭제는 작성자와 프로젝트 관리자. 서버가 따로 판단해 내려준다. */
  canEdit: boolean;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string;
}

export const commentApi = {
  list: (issueId: number) => api.get<Comment[]>(`/api/issues/${issueId}/comments`),
  write: (issueId: number, content: string) => api.post<Comment>(`/api/issues/${issueId}/comments`, { content }),
  edit: (id: number, content: string) => api.patch<Comment>(`/api/comments/${id}`, { content }),
  remove: (id: number) => api.delete<void>(`/api/comments/${id}`),
};
