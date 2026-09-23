/** 백엔드 DTO 와 1:1 로 맞춘 타입. 서버 응답 형태가 바뀌면 여기만 고친다. */

export interface UserResponse {
  id: number;
  /** 소셜 회원은 이메일을 못 받았을 수 있다. */
  email: string | null;
  name: string;
  provider: 'LOCAL' | 'KAKAO' | 'NAVER';
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

/** 백엔드 GlobalExceptionHandler 가 내려주는 통일된 에러 포맷. */
export interface ErrorResponse {
  status: number;
  code: string;
  message: string;
  errors?: { field: string; message: string }[];
}

/** fetch 는 4xx/5xx 에도 reject 하지 않으므로, 직접 던져서 호출부가 catch 할 수 있게 한다. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors?: { field: string; message: string }[],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
