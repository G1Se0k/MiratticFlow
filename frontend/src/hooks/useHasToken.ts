'use client';

import { useEffect, useState } from 'react';
import { tokens } from '@/lib/auth/tokens';

/**
 * 토큰 보유 여부. 마운트 전에는 null 을 돌려준다.
 *
 * localStorage 는 서버에 없다. 렌더 중에 바로 읽으면
 * 서버가 그린 HTML(토큰 없음)과 클라이언트 첫 렌더(토큰 있음)가 달라져 hydration 이 깨진다.
 * 첫 렌더는 양쪽 모두 null 로 맞추고, 마운트된 뒤에 읽는다.
 */
export function useHasToken(): boolean | null {
  const [hasToken, setHasToken] = useState<boolean | null>(null);

  useEffect(() => {
    setHasToken(tokens.getAccess() !== null);
  }, []);

  return hasToken;
}
