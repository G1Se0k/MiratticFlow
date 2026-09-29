import { api, logout } from './client';
import type { UserResponse } from './types';

/**
 * 로그인 · 가입 · 비밀번호는 Mirattic 계정(auth.mirattic.com)의 일이다. Flow 에는 로그아웃 · 내 정보 · 이름만 있다.
 * 탈퇴는 API 가 아니라 Auth 에서 비밀번호를 다시 확인하는 페이지 이동이다 (withdrawUrl).
 * 토큰은 HttpOnly 쿠키라 여기서 저장하거나 지울 것이 없다 (서버가 쿠키를 지운다).
 */
export const authApi = {
  /** 진행 중인 재발급이 끝난 뒤에 로그아웃한다 (그래야 가장 최근 refresh token 을 폐기하고, 늦은 재발급이 쿠키를 되살리지 않는다). */
  logout,

  getMe: () => api.get<UserResponse>('/api/users/me'),

  updateName: (name: string) => api.patch<UserResponse>('/api/users/me', { name }),
};
