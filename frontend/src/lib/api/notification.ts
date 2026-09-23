import { api } from './client';

export type NotificationType =
  | 'ISSUE_ASSIGNED'
  | 'ISSUE_STATUS_CHANGED'
  | 'COMMENT_ADDED'
  | 'PROJECT_JOINED';

export interface Notification {
  id: number;
  type: NotificationType;
  /** 서버가 만들 때 굳혀 둔 문장. 화면에서 조립하지 않는다. */
  content: string;
  /** 누르면 갈 곳. 예: "/issues/12" */
  link: string;
  read: boolean;
  createdAt: string;
}

export const notificationApi = {
  list: () => api.get<Notification[]>('/api/notifications'),
  unreadCount: () => api.get<{ count: number }>('/api/notifications/unread-count'),
  markRead: (id: number) => api.patch<void>(`/api/notifications/${id}/read`, {}),
  markAllRead: () => api.patch<void>('/api/notifications/read-all', {}),
};
