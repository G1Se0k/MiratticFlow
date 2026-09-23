'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '@/lib/api/notification';

export const notificationKeys = {
  list: ['notifications'] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
};

/**
 * 안 읽은 수만 30초마다 다시 받는다.
 *
 * WebSocket 으로 밀 수도 있지만, 채팅 독을 접으면 STOMP 연결이 끊기도록 만들어 두었다.
 * 채팅을 안 볼 때는 연결이 없으니 실시간 알림이 닿지 않는다.
 * 알림은 몇 초 늦어도 되는 정보라 폴링이 맞다. 목록은 종을 열 때만 받는다.
 */
export const useUnreadCount = () =>
  useQuery({
    queryKey: notificationKeys.unreadCount,
    queryFn: () => notificationApi.unreadCount().then((r) => r.count),
    refetchInterval: 30_000,
  });

export const useNotifications = (enabled: boolean) =>
  useQuery({
    queryKey: notificationKeys.list,
    queryFn: () => notificationApi.list(),
    enabled,
  });

export function useNotificationMutations() {
  const queryClient = useQueryClient();
  // ['notifications'] 는 배지 키의 접두사라 목록과 안 읽은 수가 함께 갱신된다.
  // 따로 무효화하면 한쪽만 남아 숫자와 표시가 어긋난다.
  const refresh = () => queryClient.invalidateQueries({ queryKey: notificationKeys.list });

  return {
    markRead: useMutation({ mutationFn: notificationApi.markRead, onSuccess: refresh }),
    markAllRead: useMutation({ mutationFn: notificationApi.markAllRead, onSuccess: refresh }),
  };
}
