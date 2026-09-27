'use client';

import { PROVIDER_LABEL, startOAuthLogin, type OAuthProvider } from '@/lib/auth/oauth';

/** 각 서비스의 브랜드 가이드 색상 */
const STYLE: Record<OAuthProvider, string> = {
  kakao: 'bg-[#FEE500] text-[#191600] hover:brightness-[0.97]',
  naver: 'bg-[#03C75A] text-white hover:brightness-[0.97]',
};

/** 브랜드 마크는 가이드에 정해진 모양이라 아이콘 세트에 두지 않고 여기서만 쓴다. */
const MARK: Record<OAuthProvider, React.ReactNode> = {
  kakao: (
    <path d="M12 3.5c-4.69 0-8.5 2.94-8.5 6.56 0 2.34 1.55 4.4 3.87 5.56-.17.62-.62 2.28-.71 2.63-.1.43.16.42.34.31.14-.09 2.22-1.5 3.12-2.12.61.09 1.24.14 1.88.14 4.69 0 8.5-2.94 8.5-6.52S16.69 3.5 12 3.5z" />
  ),
  naver: <path d="M16.27 12.85 7.38 0H0v24h7.73V11.16L16.62 24H24V0h-7.73v12.85z" />,
};

export function SocialButton({ provider, disabled }: { provider: OAuthProvider; disabled?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => startOAuthLogin(provider)}
      className={`flex h-9 w-full items-center justify-center gap-2 rounded-md text-[13px] font-medium transition disabled:cursor-not-allowed disabled:opacity-45 ${STYLE[provider]}`}
    >
      <svg viewBox="0 0 24 24" fill="currentColor" className="size-3.5" aria-hidden>
        {MARK[provider]}
      </svg>
      {PROVIDER_LABEL[provider]}로 로그인
    </button>
  );
}
