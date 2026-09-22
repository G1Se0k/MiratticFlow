import Image from 'next/image';
import Link from 'next/link';

/**
 * 심볼(이미지) + 워드마크(CSS 텍스트) 조합.
 * 원본 로고의 "Mirattic" 글자는 진한 남색이라 다크 모드에서 보이지 않는다.
 * 글자를 CSS 로 그리면 테마를 따라가고, 확대해도 깨지지 않는다.
 */
export function Logo({ size = 'md', href }: { size?: 'sm' | 'md' | 'lg'; href?: string }) {
  const mark = { sm: 28, md: 36, lg: 56 }[size];
  const text = { sm: 'text-base', md: 'text-lg', lg: 'text-2xl' }[size];

  const content = (
    <span className="inline-flex items-center gap-2.5">
      <Image src="/logo-mark.png" alt="" width={mark} height={mark} priority className="shrink-0" />
      <span className={`font-semibold tracking-tight ${text}`}>
        Mirattic <span className="brand-gradient-text">Flow</span>
      </span>
    </span>
  );

  return href ? (
    <Link href={href} aria-label="Mirattic Flow 홈">
      {content}
    </Link>
  ) : (
    content
  );
}

/** 로그인·회원가입 화면용. 태그라인까지 포함한 세로 배치. */
export function LogoBlock() {
  return (
    <div className="mb-8 flex flex-col items-center gap-3">
      <Image src="/logo-mark.png" alt="" width={64} height={64} priority />
      <div className="text-center">
        <p className="text-2xl font-semibold tracking-tight">
          Mirattic <span className="brand-gradient-text">Flow</span>
        </p>
        <p className="mt-1 text-xs tracking-wide text-slate-400">Ideas Live. Teams Thrive.</p>
      </div>
    </div>
  );
}
