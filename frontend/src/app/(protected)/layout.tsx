'use client';

import { useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { Spinner } from '@/components/ui/Spinner';
import { useMe } from '@/hooks/useAuth';
import { tokens } from '@/lib/auth/tokens';

/**
 * 인증 가드.
 * 토큰을 localStorage 에 두기로 했으므로 서버(미들웨어)에서는 읽을 수 없다.
 * 따라서 보호는 클라이언트에서 한다 — 화면을 가리는 용도일 뿐,
 * 실제 데이터 보호는 백엔드가 매 요청 토큰을 검증해서 한다.
 */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { data: user, isPending, isError } = useMe();
  const hasToken = tokens.getAccess() !== null;

  useEffect(() => {
    if (!hasToken || isError) router.replace('/login');
  }, [hasToken, isError, router]);

  if (!hasToken || isError) return null;
  if (isPending || !user) return <Spinner />;

  return <>{children}</>;
}
