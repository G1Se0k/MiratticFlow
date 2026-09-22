/**
 * 토큰 저장소. localStorage 접근은 이 파일에서만 한다.
 * 저장 위치를 나중에 쿠키로 바꾸더라도 고칠 곳은 여기뿐이다.
 *
 * localStorage 는 XSS 에 취약하다는 한계가 있다 (스크립트가 주입되면 토큰을 읽어갈 수 있음).
 * HttpOnly 쿠키가 더 안전하지만, 그 경우 CORS credentials 와 SameSite 설정이 따라붙는다.
 */
const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

// 서버 렌더링 중에는 window 가 없다.
const storage = () => (typeof window === 'undefined' ? null : window.localStorage);

export const tokens = {
  getAccess: () => storage()?.getItem(ACCESS_TOKEN_KEY) ?? null,
  getRefresh: () => storage()?.getItem(REFRESH_TOKEN_KEY) ?? null,

  save(accessToken: string, refreshToken: string) {
    storage()?.setItem(ACCESS_TOKEN_KEY, accessToken);
    storage()?.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },

  clear() {
    storage()?.removeItem(ACCESS_TOKEN_KEY);
    storage()?.removeItem(REFRESH_TOKEN_KEY);
  },
};
