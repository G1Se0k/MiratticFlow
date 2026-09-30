import Link from 'next/link';
import type { ReactNode } from 'react';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    // 로그인 화면만 Mirattic/DESIGN .md(주색 #2563eb, 12px 모서리, 떠 있는 카드)를 따른다.
    <main className="flex min-h-dvh flex-col items-center justify-center gap-5 bg-[#f2f4f6] px-4 py-10 dark:bg-[#101318]">
      <div className="w-full max-w-[24rem] rounded-[12px] border border-[#e5e8eb] bg-white p-8 shadow-[0_1px_3px_rgb(0_0_0/0.04),0_12px_32px_-12px_rgb(0_0_0/0.14)] dark:border-[#2c323b] dark:bg-[#1a1e25] dark:shadow-[0_1px_2px_rgb(0_0_0/0.3),0_12px_32px_-12px_rgb(0_0_0/0.6)]">
        {children}
      </div>
      <p className="text-[12px] text-[#4e5968] dark:text-[#b0b8c1]">
        <Link href="/terms" className="hover:text-ink">
          이용약관
        </Link>
        <span className="px-1.5">·</span>
        <Link href="/privacy" className="hover:text-ink">
          개인정보처리방침
        </Link>
      </p>
    </main>
  );
}
