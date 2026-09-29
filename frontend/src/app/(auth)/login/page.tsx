'use client';

import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { LogoBlock } from '@/components/ui/Logo';
import { FormError } from '@/components/ui/FormField';
import { Notice } from '@/components/ui/Notice';
import { loginUrl } from '@/lib/api/client';

const ERRORS: Record<string, string> = {
  failed: '로그인하지 못했습니다. 다시 시도해주세요.',
  expired: '로그인 시간이 지났습니다. 다시 시도해주세요.',
  busy: '지금 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.',
};

/**
 * 로그인 · 가입은 Mirattic 계정(auth.mirattic.com)에서 한다 — 이메일, 카카오, 네이버 모두.
 * 버튼은 백엔드의 /auth/start 로 이동할 뿐이고, 돌아오면 HttpOnly 쿠키로 로그인된 상태다.
 */
function Login() {
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect');
  // 슬래시 하나로 시작하는 내부 경로만 넘긴다 (서버도 한 번 더 막는다).
  const next = redirect && /^\/(?![/\\])/.test(redirect) ? redirect : '/workspaces';
  const error = searchParams.get('error');

  return (
    <div className="flex flex-col gap-4">
      <LogoBlock />

      {(searchParams.get('withdraw') === 'success' || searchParams.get('state') === 'withdraw') && (
        <Notice>탈퇴가 완료되었습니다. 그동안 이용해주셔서 감사합니다.</Notice>
      )}
      {searchParams.get('logout') === 'success' && searchParams.get('state') !== 'withdraw' && (
        <Notice>로그아웃되었습니다.</Notice>
      )}
      {error && <FormError>{ERRORS[error] ?? ERRORS.failed}</FormError>}

      {/* 페이지 이동이다 (fetch 가 아니다). Link 는 클라이언트 라우팅을 하므로 a 를 쓴다. */}
      <a
        href={loginUrl(next)}
        className="flex h-10 w-full items-center justify-center rounded-md bg-ink text-[14px] font-medium text-canvas transition-opacity hover:opacity-90"
      >
        Mirattic 계정으로 로그인
      </a>
    </div>
  );
}

export default function LoginPage() {
  // useSearchParams 는 CSR 경계가 필요하다.
  return (
    <Suspense fallback={null}>
      <Login />
    </Suspense>
  );
}
