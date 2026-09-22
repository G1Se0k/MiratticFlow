import { api } from './client';
import type { TokenResponse, UserResponse } from './types';
import { tokens } from '../auth/tokens';

export interface SignupPayload {
  email: string;
  password: string;
  name: string;
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

  getMe: () => api.get<UserResponse>('/api/users/me'),
};
