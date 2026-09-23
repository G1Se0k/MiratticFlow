import { ApiError, type ErrorResponse, type TokenResponse } from './types';
import { tokens } from '../auth/tokens';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

/**
 * 진행 중인 재발급 요청 하나를 모두가 공유한다.
 *
 * 화면 진입 시 여러 요청이 동시에 나가면 access token 이 만료된 경우 전부 401 을 받는다.
 * 각자 재발급을 호출하면 백엔드가 refresh 를 rotation 하므로 첫 번째만 성공하고
 * 나머지는 "이미 폐기된 토큰"으로 실패해 로그아웃돼 버린다.
 * 그래서 재발급은 한 번만 실행하고, 나머지는 그 결과를 기다린다.
 */
let refreshPromise: Promise<boolean> | null = null;

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ErrorResponse;
    return new ApiError(response.status, body.code, body.message, body.errors);
  } catch {
    // 서버가 죽었거나 JSON 이 아닌 응답
    return new ApiError(response.status, 'UNKNOWN', '서버와 통신하지 못했습니다.');
  }
}

async function refreshTokens(): Promise<boolean> {
  const refreshToken = tokens.getRefresh();
  if (!refreshToken) return false;

  const response = await fetch(`${BASE_URL}/api/auth/reissue`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    tokens.clear();
    return false;
  }
  const { accessToken, refreshToken: newRefreshToken } = (await response.json()) as TokenResponse;
  tokens.save(accessToken, newRefreshToken);
  return true;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** 로그인·회원가입처럼 토큰이 필요 없는 요청. 401 재발급도 시도하지 않는다. */
  auth?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options;

  const send = () => {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const accessToken = auth ? tokens.getAccess() : null;
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    return fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let response = await send();

  // access token 만료 → 재발급 후 원래 요청을 한 번만 재시도한다.
  if (response.status === 401 && auth && tokens.getRefresh()) {
    refreshPromise ??= refreshTokens().finally(() => {
      refreshPromise = null;
    });
    const refreshed = await refreshPromise;
    if (!refreshed) {
      tokens.clear();
      throw await parseError(response);
    }
    response = await send();
  }

  if (!response.ok) throw await parseError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, auth = true) => request<T>(path, { auth }),
  post: <T>(path: string, body?: unknown, auth = true) => request<T>(path, { method: 'POST', body, auth }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string, body?: unknown) => request<T>(path, { method: 'DELETE', body }),
};
