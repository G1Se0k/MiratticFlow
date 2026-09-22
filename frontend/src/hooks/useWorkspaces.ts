'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { workspaceApi, type WorkspaceRole } from '@/lib/api/workspace';

export const workspaceKeys = {
  all: ['workspaces'] as const,
  detail: (id: number) => ['workspaces', id] as const,
  members: (id: number) => ['workspaces', id, 'members'] as const,
  links: (id: number) => ['workspaces', id, 'links'] as const,
  joinCode: (id: number) => ['workspaces', id, 'joinCode'] as const,
};

export const useWorkspaces = () =>
  useQuery({ queryKey: workspaceKeys.all, queryFn: workspaceApi.list });

export const useWorkspace = (id: number) =>
  useQuery({ queryKey: workspaceKeys.detail(id), queryFn: () => workspaceApi.get(id) });

export const useMembers = (id: number) =>
  useQuery({ queryKey: workspaceKeys.members(id), queryFn: () => workspaceApi.members(id) });

/** 초대 관리는 OWNER 만 볼 수 있다. MEMBER 가 열면 403 이므로 아예 요청하지 않는다. */
export const useInviteLinks = (id: number, enabled: boolean) =>
  useQuery({ queryKey: workspaceKeys.links(id), queryFn: () => workspaceApi.links(id), enabled });

export const useJoinCode = (id: number, enabled: boolean) =>
  useQuery({ queryKey: workspaceKeys.joinCode(id), queryFn: () => workspaceApi.joinCode(id), enabled });

export function useCreateWorkspace() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workspaceApi.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: workspaceKeys.all }),
  });
}

export function useUpdateWorkspace(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; description?: string }) => workspaceApi.update(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
      queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(id) });
    },
  });
}

export function useMemberMutations(id: number) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: workspaceKeys.members(id) });

  return {
    changeRole: useMutation({
      mutationFn: ({ userId, role }: { userId: number; role: WorkspaceRole }) =>
        workspaceApi.changeRole(id, userId, role),
      onSuccess: () => {
        refresh();
        // 내가 OWNER 를 넘겼다면 내 역할 표시도 달라진다.
        queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(id) });
      },
    }),
    removeMember: useMutation({
      mutationFn: (userId: number) => workspaceApi.removeMember(id, userId),
      onSuccess: refresh,
    }),
  };
}

export function useInviteMutations(id: number) {
  const queryClient = useQueryClient();

  return {
    createLink: useMutation({
      mutationFn: () => workspaceApi.createLink(id),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: workspaceKeys.links(id) }),
    }),
    revokeLink: useMutation({
      mutationFn: (inviteId: number) => workspaceApi.revokeLink(inviteId),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: workspaceKeys.links(id) }),
    }),
    regenerateCode: useMutation({
      mutationFn: () => workspaceApi.regenerateJoinCode(id),
      onSuccess: (invite) => queryClient.setQueryData(workspaceKeys.joinCode(id), invite),
    }),
  };
}
