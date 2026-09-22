'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { authApi, type LoginPayload, type SignupPayload } from '@/lib/api/auth';
import { ApiError } from '@/lib/api/types';
import { tokens } from '@/lib/auth/tokens';

export const ME_QUERY_KEY = ['me'] as const;

/**
 * 로그인 여부를 따로 저장하지 않고 "내 정보 조회가 성공하는가"로 판단한다.
 * 상태를 두 군데(전역 store + 서버)에 두면 반드시 어긋난다.
 */
export function useMe() {
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: authApi.getMe,
    // 토큰이 아예 없으면 요청을 보낼 필요도 없다.
    enabled: typeof window !== 'undefined' && tokens.getAccess() !== null,
    retry: (failureCount, error) => {
      // 401/403 은 재시도해도 결과가 같다. 네트워크 오류만 한 번 더 시도한다.
      if (error instanceof ApiError && error.status < 500) return false;
      return failureCount < 1;
    },
  });
}

export function useLogin() {
  const router = useRouter();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: LoginPayload) => authApi.login(payload),
    onSuccess: async () => {
      // 토큰이 바뀌었으니 캐시된 내 정보를 버리고 다시 가져온다.
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
      router.replace('/dashboard');
    },
  });
}

export function useSignup() {
  const router = useRouter();

  return useMutation({
    mutationFn: (payload: SignupPayload) => authApi.signup(payload),
    onSuccess: () => router.replace('/login?signup=success'),
  });
}

export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      queryClient.clear();
      router.replace('/login');
    },
  });
}
