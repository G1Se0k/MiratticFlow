'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { projectApi, type ProjectInput } from '@/lib/api/project';

export const projectKeys = {
  list: (workspaceId: number) => ['workspaces', workspaceId, 'projects'] as const,
  detail: (id: number) => ['projects', id] as const,
  members: (id: number) => ['projects', id, 'members'] as const,
};

export const useProjects = (workspaceId: number) =>
  useQuery({ queryKey: projectKeys.list(workspaceId), queryFn: () => projectApi.list(workspaceId) });

export const useProject = (id: number) =>
  useQuery({ queryKey: projectKeys.detail(id), queryFn: () => projectApi.get(id) });

export const useProjectMembers = (id: number) =>
  useQuery({ queryKey: projectKeys.members(id), queryFn: () => projectApi.members(id) });

export function useCreateProject(workspaceId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ProjectInput) => projectApi.create(workspaceId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectKeys.list(workspaceId) }),
  });
}

export function useProjectMutations(id: number, workspaceId?: number) {
  const queryClient = useQueryClient();
  const refreshDetail = () => {
    queryClient.invalidateQueries({ queryKey: projectKeys.detail(id) });
    // 이름·상태·참여자 수가 목록에도 보이므로 함께 갱신한다.
    if (workspaceId) queryClient.invalidateQueries({ queryKey: projectKeys.list(workspaceId) });
  };
  const refreshMembers = () => {
    queryClient.invalidateQueries({ queryKey: projectKeys.members(id) });
    if (workspaceId) queryClient.invalidateQueries({ queryKey: projectKeys.list(workspaceId) });
  };

  return {
    update: useMutation({
      mutationFn: (body: ProjectInput) => projectApi.update(id, body),
      onSuccess: refreshDetail,
    }),
    remove: useMutation({
      mutationFn: () => projectApi.remove(id),
      onSuccess: refreshDetail,
    }),
    addMember: useMutation({
      mutationFn: (userId: number) => projectApi.addMember(id, userId),
      onSuccess: refreshMembers,
    }),
    removeMember: useMutation({
      mutationFn: (userId: number) => projectApi.removeMember(id, userId),
      onSuccess: refreshMembers,
    }),
  };
}
