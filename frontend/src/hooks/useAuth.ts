'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useToast } from '@/components/ui/Toast';
import { authApi } from '@/lib/api/auth';
import { endAuthSession } from '@/lib/api/client';
import { ApiError } from '@/lib/api/types';

export const ME_QUERY_KEY = ['me'] as const;

/**
 * 로그인 여부를 따로 저장하지 않고 "내 정보 조회가 성공하는가"로 판단한다.
 * 토큰은 HttpOnly 쿠키라 스크립트가 볼 수 없으므로, 서버에 물어보는 것이 유일한 방법이기도 하다.
 */
export function useMe() {
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: authApi.getMe,
    retry: (failureCount, error) => {
      // 401/403 은 재시도해도 결과가 같다. 네트워크 오류만 한 번 더 시도한다.
      if (error instanceof ApiError && error.status < 500) return false;
      return failureCount < 1;
    },
  });
}

/** 이름 변경. 응답으로 온 사용자를 캐시에 그대로 넣는다 (다시 조회할 이유가 없다). */
export function useUpdateName() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => authApi.updateName(name),
    onSuccess: (user) => queryClient.setQueryData(ME_QUERY_KEY, user),
  });
}

export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const toast = useToast();

  return useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: (info) => {
      queryClient.clear();
      // Auth 의 로그인 세션까지 끝낸다. 돌아올 곳은 /login?logout=success.
      if (!endAuthSession(info)) router.replace('/login?logout=success');
    },
    // 로그인 쿠키는 HttpOnly 라 서버만 지울 수 있다. 실패했는데 "로그아웃됨"을 보여주면
    // 공용 PC 에서 다음 사람이 그대로 들어올 수 있다. 실패를 알리고 다시 누르게 한다.
    onError: () => toast('로그아웃하지 못했습니다. 인터넷 연결을 확인하고 다시 시도해주세요.', 'error'),
  });
}
