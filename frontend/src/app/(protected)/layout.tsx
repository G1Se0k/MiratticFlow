'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { NotificationBell } from '@/components/notification/NotificationBell';
import { Button } from '@/components/ui/Button';
import { Logo } from '@/components/ui/Logo';
import { Spinner } from '@/components/ui/Spinner';
import { useLogout, useMe } from '@/hooks/useAuth';
import { useHasToken } from '@/hooks/useHasToken';

/**
 * 인증 가드.
 * 토큰이 localStorage 에 있어 서버(미들웨어)에서는 읽을 수 없으므로 클라이언트에서 가린다.
 * 실제 데이터 보호는 백엔드가 매 요청 토큰을 검증해서 한다.
 */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { data: user, isPending, isError } = useMe();
  const logout = useLogout();
  const hasToken = useHasToken();

  useEffect(() => {
    if (hasToken === null) return; // 아직 확인 전
    // 로그인 후 원래 가려던 곳으로 돌아오게 한다.
    // 초대 링크를 로그아웃 상태로 열었을 때 특히 필요하다.
    if (!hasToken || isError) {
      router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
    }
  }, [hasToken, isError, pathname, router]);

  // 서버와 클라이언트 첫 렌더를 null 로 맞춘다 (hydration 불일치 방지)
  if (hasToken === null) return null;
  if (!hasToken || isError) return null;
  if (isPending || !user) return <Spinner />;

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-950/80">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Logo size="sm" href="/workspaces" />
          <div className="flex items-center gap-2">
            <NotificationBell />
            <Link
              href="/account"
              className="hidden text-sm text-slate-500 hover:text-slate-900 sm:inline dark:hover:text-slate-100"
            >
              {user.name}
            </Link>
            <Button variant="ghost" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
              로그아웃
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>
    </div>
  );
}
