'use client';

import Link from 'next/link';
import { useParams, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useRef, useState } from 'react';
import { Spinner } from '@/components/ui/Spinner';
import { useOAuthLogin } from '@/hooks/useAuth';
import { consumeState, isOAuthProvider, PROVIDER_LABEL } from '@/lib/auth/oauth';

function Callback() {
  const params = useParams<{ provider: string }>();
  const searchParams = useSearchParams();
  const oauthLogin = useOAuthLogin();

  const [error, setError] = useState<string | null>(null);
  // 인가 코드는 일회용이다. 개발 모드의 이중 실행으로 두 번 교환하면 반드시 실패한다.
  const started = useRef(false);

  const provider = params.provider;
  const code = searchParams.get('code');
  const returnedState = searchParams.get('state');
  const providerError = searchParams.get('error');

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    if (providerError) return setError('로그인이 취소되었습니다.');
    if (!isOAuthProvider(provider)) return setError('지원하지 않는 소셜 로그인입니다.');
    if (!code) return setError('인가 코드가 전달되지 않았습니다.');

    // state 대조는 CSRF 방어의 핵심이다.
    // 저장된 state 가 없다는 것은 이 브라우저에서 로그인을 시작하지 않았다는 뜻이므로,
    // "없으면 통과"시키면 방어가 통째로 무력해진다. 반드시 존재하고 일치해야 한다.
    const savedState = consumeState();
    if (!savedState || returnedState !== savedState) {
      return setError('로그인 요청이 유효하지 않습니다. 다시 시도해주세요.');
    }

    oauthLogin.mutate({ provider, code, state: returnedState });
    // oauthLogin 은 렌더마다 새 객체다. 위 started 가드로 1회 실행이 보장된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [provider, code, returnedState, providerError]);

  const message = error ?? (oauthLogin.isError ? oauthLogin.error.message : null);

  if (message) {
    return (
      <div className="flex flex-col gap-4 text-center">
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {message}
        </p>
        <Link href="/login" className="text-sm font-medium underline">
          로그인으로 돌아가기
        </Link>
      </div>
    );
  }

  const label = isOAuthProvider(provider) ? PROVIDER_LABEL[provider] : provider;
  return <Spinner label={`${label} 계정으로 로그인 중...`} />;
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <Callback />
    </Suspense>
  );
}
