import Link from 'next/link';
import type { ReactNode } from 'react';
import { Icon } from '@/components/ui/Icon';

/**
 * 약관 문서 전용 레이아웃. 가드가 걸린 (protected) 바깥에 있어 비로그인 상태로도 열린다.
 * 본문 스타일은 자손 선택자로 한 번만 정의하고, 각 페이지는 의미 있는 HTML 만 갖는다.
 */
export default function LegalLayout({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto max-w-[42rem] px-5 py-12">
      <Link
        href="/"
        className="flex w-fit items-center gap-1 text-[13px] text-ink-faint transition-colors hover:text-ink"
      >
        <Icon name="arrowLeft" className="size-3.5" />
        Mirattic Flow
      </Link>

      <article
        className="mt-8 flex flex-col gap-7 text-[13px] leading-[1.75] text-ink-soft
          [&_h1]:text-xl [&_h1]:font-semibold [&_h1]:text-ink
          [&_h2]:mb-1.5 [&_h2]:text-[15px] [&_h2]:font-semibold [&_h2]:text-ink
          [&_li]:mt-1 [&_ul]:list-disc [&_ul]:pl-5
          [&_section]:flex [&_section]:flex-col
          [&_p]:mt-2 [&_h2+p]:mt-0
          [&_a]:text-accent [&_a]:underline [&_a]:underline-offset-2"
      >
        {children}
      </article>
    </main>
  );
}
