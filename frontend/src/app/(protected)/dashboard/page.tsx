'use client';

import { Button } from '@/components/ui/Button';
import { useLogout, useMe } from '@/hooks/useAuth';

/** Phase 2 확인용 임시 화면. 실제 대시보드는 Phase 9 에서 만든다. */
export default function DashboardPage() {
  const { data: user } = useMe();
  const logout = useLogout();

  return (
    <main className="mx-auto flex min-h-dvh max-w-2xl flex-col gap-6 px-4 py-12">
      <header className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight">Mirattic Flow</h1>
        <Button variant="ghost" onClick={() => logout.mutate()} disabled={logout.isPending}>
          로그아웃
        </Button>
      </header>

      <section className="rounded-lg border border-slate-200 p-6 dark:border-slate-800">
        <h2 className="mb-4 text-sm font-medium text-slate-500">로그인한 사용자</h2>
        <dl className="grid grid-cols-[6rem_1fr] gap-y-2 text-sm">
          <dt className="text-slate-500">이름</dt>
          <dd>{user?.name}</dd>
          <dt className="text-slate-500">이메일</dt>
          <dd>{user?.email}</dd>
          <dt className="text-slate-500">가입일</dt>
          <dd>{user ? new Date(user.createdAt).toLocaleString('ko-KR') : null}</dd>
        </dl>
      </section>

      <p className="text-sm text-slate-500">워크스페이스는 Phase 3 에서 추가됩니다.</p>
    </main>
  );
}
