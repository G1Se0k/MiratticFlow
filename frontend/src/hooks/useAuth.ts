'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
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
    // 토큰이 없으면 요청을 보낼 필요도 없다. 서버 렌더 중에는 항상 꺼진다.
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
  const redirectTo = useRedirectTarget();

  return useMutation({
    mutationFn: (payload: LoginPayload) => authApi.login(payload),
    onSuccess: async () => {
      // 토큰이 바뀌었으니 캐시된 내 정보를 버리고 다시 가져온다.
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
      router.replace(redirectTo);
    },
  });
}

/**
 * ?redirect= 로 넘어온 경로로 돌려보낸다.
 * 외부 URL 이 들어오면 그대로 따라가지 않는다 (open redirect 방지).
 */
function useRedirectTarget() {
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect');
  // 슬래시 하나로 시작하는 내부 경로만 허용. 브라우저가 역슬래시를 슬래시로
  // 바꿔 읽기 때문에 `/\evil.com` 도 `//evil.com` 과 같이 외부로 나간다.
  return redirect && /^\/(?![/\\])/.test(redirect) ? redirect : '/workspaces';
}

export function useSignup() {
  const router = useRouter();

  return useMutation({
    mutationFn: (payload: SignupPayload) => authApi.signup(payload),
    onSuccess: () => router.replace('/login?signup=success'),
  });
}

export function useOAuthLogin() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const redirectTo = useRedirectTarget();

  return useMutation({
    mutationFn: ({ provider, code, state }: { provider: string; code: string; state: string | null }) =>
      authApi.oauthLogin(provider, code, state),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
      router.replace(redirectTo);
    },
  });
}

/** 탈퇴는 로그아웃과 뒷정리가 같다. 토큰을 버리고 캐시를 비운 뒤 로그인 화면으로 보낸다. */
export function useWithdraw() {
  const router = useRouter();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (password: string | null) => authApi.withdraw(password),
    onSuccess: () => {
      queryClient.clear();
      router.replace('/login?withdraw=success');
    },
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
