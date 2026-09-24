import { api } from './client';
import type { TokenResponse, UserResponse } from './types';
import { tokens } from '../auth/tokens';

export interface SignupPayload {
  email: string;
  password: string;
  name: string;
}

/** 보낸 필드만 바뀐다. 이메일 · 비밀번호를 바꿀 때는 currentPassword 가 필요하다. */
export interface UpdateMePayload {
  name?: string;
  email?: string;
  currentPassword?: string;
  newPassword?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  signup: (payload: SignupPayload) => api.post<UserResponse>('/api/auth/signup', payload, false),

  async login(payload: LoginPayload) {
    const response = await api.post<TokenResponse>('/api/auth/login', payload, false);
    tokens.save(response.accessToken, response.refreshToken);
    return response;
  },

  async logout() {
    const refreshToken = tokens.getRefresh();
    // 서버에서 refresh 를 지우지 못하더라도 클라이언트 토큰은 반드시 버린다.
    try {
      if (refreshToken) await api.post<void>('/api/auth/logout', { refreshToken }, false);
    } finally {
      tokens.clear();
    }
  },

  /** 소셜 로그인. 인가 코드를 백엔드에 넘기면 일반 로그인과 같은 형태로 토큰이 돌아온다. */
  async oauthLogin(provider: string, code: string, state: string | null) {
    const response = await api.post<TokenResponse>(`/api/auth/oauth/${provider}`, { code, state }, false);
    tokens.save(response.accessToken, response.refreshToken);
    return response;
  },

  getMe: () => api.get<UserResponse>('/api/users/me'),

  /**
   * 계정 정보 수정.
   * 비밀번호를 바꾸면 서버가 이 계정의 refresh 토큰을 전부 지운다 — 이 세션도 더는 재발급받지 못한다.
   * 그대로 두면 액세스 토큰이 만료되는 15분 뒤에 영문도 모르고 튕기므로, 바로 토큰을 버리고 다시 로그인하게 한다.
   */
  async updateMe(payload: UpdateMePayload) {
    const user = await api.patch<UserResponse>('/api/users/me', payload);
    if (payload.newPassword) tokens.clear();
    return user;
  },

  /**
   * 회원 탈퇴.
   * 로그아웃과 달리 성공했을 때만 토큰을 버린다.
   * 실패한 요청에도 토큰을 지우면 계정은 남았는데 로그인만 풀려 아무것도 못 하는 상태가 된다.
   */
  async withdraw(password: string | null) {
    await api.delete<void>('/api/users/me', { password });
    tokens.clear();
  },
};
