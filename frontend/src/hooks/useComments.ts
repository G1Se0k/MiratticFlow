'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { commentApi } from '@/lib/api/comment';

export const commentKeys = {
  list: (issueId: number) => ['issues', issueId, 'comments'] as const,
};

export const useComments = (issueId: number) =>
  useQuery({
    queryKey: commentKeys.list(issueId),
    queryFn: () => commentApi.list(issueId),
    enabled: issueId > 0,
  });

export function useCommentMutations(issueId: number) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: commentKeys.list(issueId) });

  return {
    write: useMutation({ mutationFn: (content: string) => commentApi.write(issueId, content), onSuccess: refresh }),
    edit: useMutation({
      mutationFn: ({ id, content }: { id: number; content: string }) => commentApi.edit(id, content),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: number) => commentApi.remove(id), onSuccess: refresh }),
  };
}
