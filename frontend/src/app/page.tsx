'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

/** 로그인 여부는 보호된 레이아웃의 가드가 판단한다 (로그인 안 됐으면 /login 으로 보낸다). */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/workspaces');
  }, [router]);

  return null;
}
