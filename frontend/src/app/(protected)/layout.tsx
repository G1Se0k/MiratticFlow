'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { SidebarContent } from '@/components/nav/Sidebar';
import { NotificationBell } from '@/components/notification/NotificationBell';
import { Icon } from '@/components/ui/Icon';
import { Logo } from '@/components/ui/Logo';
import { Spinner } from '@/components/ui/Spinner';
import { useMe } from '@/hooks/useAuth';
import { ApiError } from '@/lib/api/types';

/**
 * 인증 가드 + 앱 셸.
 * 로그인 여부는 내 정보 조회의 성공 여부로 판단한다 (토큰은 HttpOnly 쿠키라 스크립트가 못 본다).
 * 실제 데이터 보호는 백엔드가 매 요청 토큰을 검증해서 한다.
 */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { data: user, isPending, error, refetch } = useMe();
  const [menuOpen, setMenuOpen] = useState(false);
  // 401 만 "로그인 안 됨"이다. 서버 · Auth 장애(502 등)로 로그인 화면에 보내면 멀쩡한 로그인을 버리게 된다.
  const signedOut = error instanceof ApiError && error.status === 401;

  useEffect(() => {
    // 로그인 후 원래 가려던 곳으로 돌아오게 한다.
    // 초대 링크를 로그아웃 상태로 열었을 때 특히 필요하다.
    if (signedOut) {
      router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
    }
  }, [signedOut, pathname, router]);

  // 화면을 이동하면 열려 있던 모바일 메뉴는 닫는다.
  useEffect(() => setMenuOpen(false), [pathname]);

  if (signedOut) return null;
  if (error) {
    return (
      <main className="flex min-h-dvh flex-col items-center justify-center gap-3 px-4 text-center">
        <p className="text-[14px] text-ink-soft">{error.message}</p>
        <button onClick={() => refetch()} className="text-[13px] font-medium text-accent hover:underline">
          다시 시도
        </button>
      </main>
    );
  }
  if (isPending || !user) return <Spinner />;

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_1fr]">
      <aside className="sticky top-0 hidden h-dvh flex-col border-r border-line bg-surface lg:flex">
        <SidebarContent user={user} />
      </aside>

      {/* 모바일 상단바. 사이드바는 버튼으로 열린다. */}
      <header className="sticky top-0 z-20 flex h-12 items-center gap-1 border-b border-line bg-surface/85 px-2 backdrop-blur lg:hidden">
        <button
          onClick={() => setMenuOpen(true)}
          aria-label="메뉴 열기"
          className="flex size-8 items-center justify-center rounded-md text-ink-soft transition-colors hover:bg-raised hover:text-ink"
        >
          <Icon name="menu" className="size-5" />
        </button>
        <Logo size="sm" href="/workspaces" />
        <div className="ml-auto">
          <NotificationBell />
        </div>
      </header>

      {menuOpen && (
        <>
          <button
            aria-label="메뉴 닫기"
            onClick={() => setMenuOpen(false)}
            className="fixed inset-0 z-30 bg-ink/35 lg:hidden"
          />
          <aside className="fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-line bg-surface lg:hidden">
            <SidebarContent user={user} onNavigate={() => setMenuOpen(false)} />
          </aside>
        </>
      )}

      <div className="min-w-0">
        <main className="mx-auto w-full max-w-5xl px-4 py-6 lg:px-8 lg:py-9">{children}</main>
      </div>
    </div>
  );
}
