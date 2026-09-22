'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { Spinner } from '@/components/ui/Spinner';
import { tokens } from '@/lib/auth/tokens';

/** 토큰 유무만 보고 보낸다. 토큰이 실제로 유효한지는 dashboard 의 가드가 판단한다. */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace(tokens.getAccess() ? '/dashboard' : '/login');
  }, [router]);

  return <Spinner label="" />;
}
