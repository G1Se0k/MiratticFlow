import Link from 'next/link';
import type { ReactNode } from 'react';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-4 py-10">
      <div className="w-full max-w-[21rem] rounded-card border border-line bg-surface p-6 shadow-[0_1px_2px_rgb(0_0_0/0.04)]">
        {children}
      </div>
      <p className="text-[11px] text-ink-faint">
        <Link href="/terms" className="hover:text-ink-soft">
          이용약관
        </Link>
        <span className="px-1.5">·</span>
        <Link href="/privacy" className="hover:text-ink-soft">
          개인정보처리방침
        </Link>
      </p>
    </main>
  );
}
