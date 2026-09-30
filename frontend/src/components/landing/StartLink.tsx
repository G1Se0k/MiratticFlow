'use client';

import Link from 'next/link';
import { useMe } from '@/hooks/useAuth';

/**
 * 랜딩의 시작 버튼. 로그인한 사람에게는 워크스페이스로 가는 버튼이 된다 (자동 이동은 하지 않는다 —
 * 소개 페이지를 보러 온 사람도 있다). 확인 전(또는 로그아웃 상태)에는 로그인 버튼이다.
 */
export function StartLink({ variant = 'primary' }: { variant?: 'primary' | 'quiet' }) {
  const { data: user } = useMe();
  const href = user ? '/workspaces' : '/login';
  const label = user ? '내 워크스페이스로' : variant === 'quiet' ? '로그인' : 'Mirattic 계정으로 시작하기';

  return variant === 'quiet' ? (
    <Link href={href} className="text-[14px] font-semibold text-[#1b64da] hover:underline dark:text-[#9cc2ff]">
      {label}
    </Link>
  ) : (
    <Link
      href={href}
      className="inline-flex h-12 items-center justify-center rounded-[12px] bg-[#2563eb] px-6 text-[16px] font-semibold text-white transition-[background-color,scale] hover:bg-[#1d4ed8] focus-visible:outline-offset-2 active:scale-[0.98] motion-reduce:transition-none"
    >
      {label}
    </Link>
  );
}
