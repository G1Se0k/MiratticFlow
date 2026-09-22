'use client';

import { PROVIDER_LABEL, startOAuthLogin, type OAuthProvider } from '@/lib/auth/oauth';

/** 각 서비스의 브랜드 가이드 색상 */
const STYLE: Record<OAuthProvider, string> = {
  kakao: 'bg-[#FEE500] text-[#191600] hover:brightness-95',
  naver: 'bg-[#03C75A] text-white hover:brightness-95',
};

export function SocialButton({ provider, disabled }: { provider: OAuthProvider; disabled?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => startOAuthLogin(provider)}
      className={`h-10 w-full rounded-md text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${STYLE[provider]}`}
    >
      {PROVIDER_LABEL[provider]}로 로그인
    </button>
  );
}
