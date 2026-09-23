'use client'; // 에러 경계는 반드시 클라이언트 컴포넌트여야 한다

import { useEffect } from 'react';
import { Button } from '@/components/ui/Button';

/**
 * 렌더 중 터진 예외를 받는다.
 *
 * API 에러는 각 화면이 TanStack Query 의 isError 로 이미 처리하고 있다.
 * 여기로 오는 건 그걸 빠져나온 진짜 버그다 — 그래도 영어 기본 화면 대신
 * 우리 화면을 보여주고 돌아갈 길을 준다.
 *
 * Next 16 에서 두 번째 prop 이름이 reset 에서 retry 로 바뀌었다.
 */
export default function ErrorPage({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    // 서비스라면 여기서 Sentry 같은 곳으로 보낸다. 지금은 콘솔이 전부다.
    console.error(error);
  }, [error]);

  return (
    <div className="flex min-h-[60dvh] flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-4xl" aria-hidden>
        ⚠️
      </p>
      <h1 className="text-lg font-semibold">문제가 발생했습니다</h1>
      <p className="max-w-sm text-sm text-slate-500">
        잠시 후 다시 시도해주세요. 계속 같은 화면이 보이면 새로고침해주세요.
      </p>
      {/* digest 는 서버 로그와 맞춰 볼 수 있는 식별자다. 에러 내용 자체는 사용자에게 보여주지 않는다. */}
      {error.digest && <p className="font-mono text-xs text-slate-400">{error.digest}</p>}
      <div className="flex gap-2">
        <Button onClick={() => retry()}>다시 시도</Button>
        <Button variant="secondary" onClick={() => (window.location.href = '/workspaces')}>
          워크스페이스로
        </Button>
      </div>
    </div>
  );
}
