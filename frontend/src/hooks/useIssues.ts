'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import { projectKeys } from '@/hooks/useProjects';
import { issueApi, type IssueFilter, type IssueInput, type IssueStatus } from '@/lib/api/issue';

export const issueKeys = {
  list: (projectId: number, filter: IssueFilter) => ['projects', projectId, 'issues', filter] as const,
  lists: (projectId: number) => ['projects', projectId, 'issues'] as const,
  detail: (id: number) => ['issues', id] as const,
};

/**
 * 이슈가 바뀌면 목록만 달라지는 게 아니라 대시보드 집계도 달라진다.
 * 두 곳을 항상 같이 무효화해야 해서 한 군데에 모아 둔다 —
 * 호출하는 쪽마다 적으면 언젠가 한 곳에서 빠지고, 숫자만 옛날 값으로 남는다.
 */
function refreshProjectIssues(queryClient: QueryClient, projectId: number) {
  queryClient.invalidateQueries({ queryKey: issueKeys.lists(projectId) });
  queryClient.invalidateQueries({ queryKey: projectKeys.stats(projectId) });
}

/** 필터가 쿼리 키에 들어가므로 조건을 바꾸면 자동으로 다시 조회된다. */
export const useIssues = (projectId: number, filter: IssueFilter) =>
  useQuery({
    queryKey: issueKeys.list(projectId, filter),
    queryFn: () => issueApi.search(projectId, filter),
    enabled: projectId > 0,
    placeholderData: (previous) => previous, // 필터를 바꿀 때 목록이 사라졌다 나타나지 않게 한다
  });

export const useIssue = (id: number) =>
  useQuery({ queryKey: issueKeys.detail(id), queryFn: () => issueApi.get(id), enabled: id > 0 });

export function useCreateIssue(projectId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: IssueInput) => issueApi.create(projectId, body),
    onSuccess: () => refreshProjectIssues(queryClient, projectId),
  });
}

export function useIssueMutations(issueId: number, projectId: number) {
  const queryClient = useQueryClient();
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: issueKeys.detail(issueId) });
    refreshProjectIssues(queryClient, projectId);
  };

  return {
    update: useMutation({ mutationFn: (body: IssueInput) => issueApi.update(issueId, body), onSuccess: refresh }),
    changeStatus: useMutation({
      mutationFn: (status: IssueStatus) => issueApi.changeStatus(issueId, status),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: () => issueApi.remove(issueId),
      onSuccess: () => {
        // 지워진 이슈를 다시 불러오면 404 가 난다. 캐시에서 아예 뺀다.
        queryClient.removeQueries({ queryKey: issueKeys.detail(issueId) });
        refreshProjectIssues(queryClient, projectId);
      },
    }),
  };
}

/** 목록에서 상태만 바로 바꿀 때. 어떤 이슈든 받을 수 있어야 해서 id 를 인자로 받는다. */
export function useQuickStatusChange(projectId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ issueId, status }: { issueId: number; status: IssueStatus }) =>
      issueApi.changeStatus(issueId, status),
    onSuccess: (issue) => {
      refreshProjectIssues(queryClient, projectId);
      queryClient.invalidateQueries({ queryKey: issueKeys.detail(issue.id) });
    },
  });
}
