import { z } from 'zod';

/**
 * 백엔드의 @Valid 규칙과 같은 조건을 건다.
 * 서버 검증을 대체하는 게 아니라 왕복을 줄이기 위한 것이다. 실제 방어선은 백엔드다.
 */
export const signupSchema = z
  .object({
    email: z.email('이메일 형식이 올바르지 않습니다.').max(100),
    name: z.string().min(1, '이름을 입력해주세요.').max(50),
    password: z.string().min(8, '비밀번호는 8자 이상이어야 합니다.').max(64),
    passwordConfirm: z.string(),
  })
  .refine((data) => data.password === data.passwordConfirm, {
    message: '비밀번호가 일치하지 않습니다.',
    path: ['passwordConfirm'],
  });

export const loginSchema = z.object({
  email: z.email('이메일 형식이 올바르지 않습니다.'),
  password: z.string().min(1, '비밀번호를 입력해주세요.'),
});

export type SignupForm = z.infer<typeof signupSchema>;
export type LoginForm = z.infer<typeof loginSchema>;
