export const OAUTH_PROVIDERS = ['kakao', 'naver'] as const;
export type OAuthProvider = (typeof OAUTH_PROVIDERS)[number];

export const PROVIDER_LABEL: Record<OAuthProvider, string> = {
  kakao: '카카오',
  naver: '네이버',
};

export function isOAuthProvider(value: string): value is OAuthProvider {
  return (OAUTH_PROVIDERS as readonly string[]).includes(value);
}

const STATE_KEY = 'oauthState';

/**
 * state 는 CSRF 방지용이다.
 * 이게 없으면 공격자가 자기 인가 코드로 만든 콜백 URL 을 피해자에게 열게 해서
 * 피해자 브라우저를 공격자 계정으로 로그인시킬 수 있다 (로그인 CSRF).
 * 우리가 만든 난수를 sessionStorage 에 두고 콜백에서 대조한다.
 */
function issueState(): string {
  const state = crypto.randomUUID();
  sessionStorage.setItem(STATE_KEY, state);
  return state;
}

export function consumeState(): string | null {
  const state = sessionStorage.getItem(STATE_KEY);
  sessionStorage.removeItem(STATE_KEY); // 한 번 쓰면 버린다.
  return state;
}

const AUTHORIZE_URL: Record<OAuthProvider, string> = {
  kakao: 'https://kauth.kakao.com/oauth/authorize',
  naver: 'https://nid.naver.com/oauth2.0/authorize',
};

const CLIENT_ID: Record<OAuthProvider, string | undefined> = {
  // client id 는 인증 URL 에 그대로 노출되는 공개 값이다. secret 은 백엔드에만 둔다.
  kakao: process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID,
  naver: process.env.NEXT_PUBLIC_NAVER_CLIENT_ID,
};

export function isConfigured(provider: OAuthProvider): boolean {
  return Boolean(CLIENT_ID[provider]);
}

/** 소셜 로그인 페이지로 이동시킨다. */
export function startOAuthLogin(provider: OAuthProvider) {
  const clientId = CLIENT_ID[provider];
  if (!clientId) throw new Error(`${PROVIDER_LABEL[provider]} client id 가 설정되지 않았습니다.`);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: `${window.location.origin}/oauth/callback/${provider}`,
    state: issueState(),
  });
  window.location.href = `${AUTHORIZE_URL[provider]}?${params}`;
}
