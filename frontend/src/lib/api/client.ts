import { ApiError, type ErrorResponse } from './types';

/**
 * 비어 있으면 같은 출처로 보낸다 (운영: Caddy 가 /api 를 백엔드로, 개발: next.config 의 rewrites).
 * 로그인 쿠키가 같은 출처에만 붙으므로 따로 둘 이유가 없다.
 */
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? '';

/**
 * 토큰은 HttpOnly 쿠키(flow_at · flow_rt)에 있어 여기서는 읽지도 쓰지도 않는다 — 브라우저가 알아서 붙인다.
 * 스크립트가 토큰에 닿을 수 없으니 XSS 가 생겨도 토큰이 새지 않는다.
 *
 * 모든 요청에 X-Requested-With 를 붙인다. 쿠키 인증은 다른 사이트의 요청에도 쿠키가 따라갈 수 있어서,
 * 서버는 상태를 바꾸는 요청에 이 헤더를 요구한다 (CsrfHeaderFilter). 다른 사이트는 이 헤더를 붙일 수 없다.
 */
const HEADERS = { 'X-Requested-With': 'flow' };

/** 재발급 결과. unavailable 은 로그인이 끝난 게 아니라 Auth 에 닿지 못한 것이다 — 로그인 화면으로 보내지 않는다. */
type Refresh = 'ok' | 'expired' | 'unavailable';

/**
 * 재발급은 브라우저 전체에서 한 번에 하나만 한다.
 *
 * Auth 는 refresh token 을 쓸 때마다 회전한다. 같은 토큰으로 두 번 재발급하면 두 번째는 거절되고,
 * 늦게 보내면 탈취로 보고 그 로그인 전체를 끊는다. 화면 진입 시 여러 요청이 동시에 401 을 받거나
 * 탭 여러 개가 동시에 만료돼도 재발급은 한 번만 일어나야 한다.
 * - 한 탭 안: 진행 중인 Promise 하나를 공유한다.
 * - 탭 사이: Web Locks 로 줄을 세운다. 뒤에 선 탭은 앞 탭이 받아 둔 새 쿠키로 재발급하므로 옛 토큰을 쓰지 않는다.
 */
let refreshPromise: Promise<Refresh> | null = null;

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ErrorResponse;
    return new ApiError(response.status, body.code, body.message, body.errors);
  } catch {
    // 서버가 죽었거나 JSON 이 아닌 응답
    return new ApiError(response.status, 'UNKNOWN', '서버와 통신하지 못했습니다.');
  }
}

async function doRefresh(): Promise<Refresh> {
  try {
    const response = await fetch(`${BASE_URL}/api/auth/refresh`, { method: 'POST', headers: HEADERS, credentials: 'include' });
    if (response.ok) return 'ok';
    return response.status === 401 ? 'expired' : 'unavailable';
  } catch {
    return 'unavailable'; // 네트워크 오류
  }
}

/**
 * 재발급 · 로그아웃은 이 잠금 안에서 한 번에 하나씩. 로그아웃이 진행 중인 재발급과 겹치면, 늦게 도착한
 * 재발급 응답이 새 쿠키를 심어 로그아웃한 뒤에 다시 로그인된 상태가 될 수 있다.
 * ponytail: Web Locks 가 없는 브라우저(2022년 이전 버전)에서는 탭 사이 보호 없이 탭 안에서만 줄을 세운다.
 */
async function withAuthLock<T>(fn: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return await navigator.locks.request('flow-auth', fn);
  }
  return fn();
}

function refreshAcrossTabs(): Promise<Refresh> {
  return withAuthLock(doRefresh);
}

/** refresh 쿠키로 새 쿠키를 받는다. */
export function refreshSession(): Promise<Refresh> {
  const running = (refreshPromise ??= refreshAcrossTabs().finally(() => {
    refreshPromise = null;
  }));
  return running;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body } = options;

  const send = () =>
    fetch(`${BASE_URL}${path}`, {
      method,
      credentials: 'include',
      headers: body === undefined ? HEADERS : { ...HEADERS, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

  let response = await send();

  // access token 만료 → 재발급 후 원래 요청을 한 번만 재시도한다.
  if (response.status === 401) {
    const refreshed = await refreshSession();
    if (refreshed === 'unavailable') {
      throw new ApiError(502, 'AUTH_UNAVAILABLE', '로그인 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.');
    }
    // expired 여도 한 번 더 보낸다: 다른 탭이 그사이 새 쿠키를 받았다면 그걸로 통한다.
    response = await send();
  }

  if (!response.ok) throw await parseError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string, body?: unknown) => request<T>(path, { method: 'DELETE', body }),
};

/** Auth 의 로그인 세션을 끝낼 폼 (OIDC RP-Initiated Logout). endpoint 가 null 이면 Flow 쪽만 로그아웃한 것이다. */
export interface LogoutResponse {
  endpoint: string | null;
  idTokenHint: string | null;
  postLogoutRedirectUri: string | null;
}

/**
 * 로그아웃. 재발급과 같은 잠금 안에서 한다 (withAuthLock). 401 재시도를 하는 request() 를 쓰지 않는다 —
 * 잠금 안에서 재발급(같은 잠금)을 기다리면 서로를 기다려 멈춘다.
 * 실패하면(502: Auth 에서 폐기 못 함) 서버가 쿠키를 지우지 않으므로 다시 시도할 수 있다.
 */
export function logout(): Promise<LogoutResponse> {
  return withAuthLock(async () => {
    const response = await fetch(`${BASE_URL}/api/auth/logout`, { method: 'POST', headers: HEADERS, credentials: 'include' });
    if (!response.ok) throw await parseError(response);
    return (await response.json()) as LogoutResponse;
  });
}

/**
 * Auth 의 로그인 세션(SSO)을 끝내러 페이지를 옮긴다. Flow 쿠키만 지우면 Auth 세션이 남아
 * 공용 PC 의 다음 사람이 "로그인" 한 번으로 이 계정에 들어온다. POST 폼으로 보낸다 — GET 이면 ID token 이
 * 주소창 · 방문 기록에 남는다. Auth 는 끝낸 뒤 postLogoutRedirectUri(/login?logout=success)로 돌려보낸다.
 * state 는 그대로 돌아온다 (예: 탈퇴 안내를 띄우려고 'withdraw').
 */
export function endAuthSession(info: LogoutResponse, state?: string): boolean {
  if (!info.endpoint || !info.idTokenHint || !info.postLogoutRedirectUri) return false;
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = info.endpoint;
  const fields: Record<string, string> = {
    id_token_hint: info.idTokenHint,
    post_logout_redirect_uri: info.postLogoutRedirectUri,
    ...(state ? { state } : {}),
  };
  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
  return true;
}

/**
 * 회원 탈퇴 확인 주소. Auth 에서 비밀번호를 다시 입력해야 콜백에서 탈퇴가 실행된다 — 쿠키의 토큰만으로는
 * 탈퇴할 수 없다. 끝나면 Auth 로그인 세션까지 끝내고 /login 으로 돌아온다. 막히면 /account?withdraw=... 로.
 */
export function withdrawUrl() {
  return '/auth/start?withdraw=true';
}

/**
 * Mirattic 계정 로그인 주소. 페이지 이동으로 시작한다 (fetch 가 아니다 — 로그인 화면은 auth.mirattic.com).
 * 늘 이 사이트 자신의 경로다 (운영 Caddy · 개발 Next 가 /auth 를 백엔드로 넘긴다) — 백엔드가 돌려보내는
 * /login · /account 같은 상대 경로가 이 사이트로 돌아오게.
 * next: 로그인 뒤 돌아올 경로. 우리 사이트 안의 경로만 서버가 받아준다.
 */
export function loginUrl(next?: string | null) {
  return `/auth/start${next ? `?next=${encodeURIComponent(next)}` : ''}`;
}
