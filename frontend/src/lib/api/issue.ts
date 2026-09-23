import { api } from './client';

export type IssueStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';
export type IssuePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export const ISSUE_STATUS_LABEL: Record<IssueStatus, string> = {
  TODO: '할 일',
  IN_PROGRESS: '진행 중',
  REVIEW: '검토',
  DONE: '완료',
};

export const ISSUE_PRIORITY_LABEL: Record<IssuePriority, string> = {
  LOW: '낮음',
  MEDIUM: '보통',
  HIGH: '높음',
  URGENT: '긴급',
};

export interface IssueSummary {
  id: number;
  number: number;
  title: string;
  status: IssueStatus;
  priority: IssuePriority;
  assigneeId: number | null;
  assigneeName: string | null;
  dueDate: string | null;
  createdAt: string;
}

export interface Issue extends IssueSummary {
  projectId: number;
  description: string | null;
  reporterName: string;
  canDelete: boolean;
  updatedAt: string;
}

export interface IssueInput {
  title: string;
  description?: string;
  status?: IssueStatus;
  priority?: IssuePriority;
  assigneeId?: number | null;
  dueDate?: string | null;
}

/** 목록 화면의 검색 조건. 비어 있는 값은 쿼리에서 빠진다. */
export interface IssueFilter {
  status?: IssueStatus | '';
  priority?: IssuePriority | '';
  assigneeId?: number | '';
  keyword?: string;
  sort?: string;
  page?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

function toQuery(filter: IssueFilter) {
  const params = new URLSearchParams();
  if (filter.status) params.set('status', filter.status);
  if (filter.priority) params.set('priority', filter.priority);
  if (filter.assigneeId) params.set('assigneeId', String(filter.assigneeId));
  if (filter.keyword?.trim()) params.set('keyword', filter.keyword.trim());
  if (filter.sort) params.set('sort', filter.sort);
  if (filter.page) params.set('page', String(filter.page));
  const query = params.toString();
  return query ? `?${query}` : '';
}

export const issueApi = {
  search: (projectId: number, filter: IssueFilter) =>
    api.get<PageResponse<IssueSummary>>(`/api/projects/${projectId}/issues${toQuery(filter)}`),
  create: (projectId: number, body: IssueInput) => api.post<Issue>(`/api/projects/${projectId}/issues`, body),

  get: (id: number) => api.get<Issue>(`/api/issues/${id}`),
  update: (id: number, body: IssueInput) => api.patch<Issue>(`/api/issues/${id}`, body),
  changeStatus: (id: number, status: IssueStatus) => api.patch<Issue>(`/api/issues/${id}/status`, { status }),
  remove: (id: number) => api.delete<void>(`/api/issues/${id}`),
};
