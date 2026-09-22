'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useHasToken } from '@/hooks/useHasToken';

/** 토큰 유무만 보고 보낸다. 토큰이 실제로 유효한지는 보호된 레이아웃의 가드가 판단한다. */
export default function HomePage() {
  const router = useRouter();
  const hasToken = useHasToken();

  useEffect(() => {
    if (hasToken === null) return;
    router.replace(hasToken ? '/workspaces' : '/login');
  }, [hasToken, router]);

  return null;
}
