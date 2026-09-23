import Link from 'next/link';
import type { ReactNode } from 'react';

/**
 * 약관 문서 전용 레이아웃. 가드가 걸린 (protected) 바깥에 있어 비로그인 상태로도 열린다.
 * 본문 스타일은 자손 선택자로 한 번만 정의하고, 각 페이지는 의미 있는 HTML 만 갖는다.
 */
export default function LegalLayout({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto max-w-2xl px-4 py-12">
      <Link
        href="/"
        className="text-sm text-slate-500 underline underline-offset-4 hover:text-slate-900 dark:hover:text-slate-100"
      >
        ← Mirattic Flow
      </Link>

      <article
        className="mt-8 flex flex-col gap-6 text-sm leading-relaxed text-slate-600 dark:text-slate-300
          [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:text-slate-900 dark:[&_h1]:text-slate-100
          [&_h2]:mb-2 [&_h2]:text-base [&_h2]:font-semibold [&_h2]:text-slate-900 dark:[&_h2]:text-slate-100
          [&_li]:mt-1 [&_ul]:list-disc [&_ul]:pl-5
          [&_section]:flex [&_section]:flex-col
          [&_p]:mt-2 [&_h2+p]:mt-0
          [&_a]:underline [&_a]:underline-offset-2"
      >
        {children}
      </article>
    </main>
  );
}
