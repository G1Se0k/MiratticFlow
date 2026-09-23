'use client';

import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/Button';
import { LogoBlock } from '@/components/ui/Logo';
import { FormError, FormField } from '@/components/ui/FormField';
import { useSignup } from '@/hooks/useAuth';
import { signupSchema, type SignupForm } from '@/lib/validation/auth';

export default function SignupPage() {
  const signup = useSignup();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupForm>({ resolver: zodResolver(signupSchema) });

  return (
    <form
      onSubmit={handleSubmit(({ email, password, name }) => signup.mutate({ email, password, name }))}
      className="flex flex-col gap-4"
      noValidate
    >
      <LogoBlock />
      <FormField label="이메일" type="email" autoComplete="email" placeholder="you@example.com"
        error={errors.email?.message} {...register('email')} />
      <FormField label="이름" autoComplete="name" placeholder="홍길동"
        error={errors.name?.message} {...register('name')} />
      <FormField label="비밀번호" type="password" autoComplete="new-password" placeholder="8자 이상"
        error={errors.password?.message} {...register('password')} />
      <FormField label="비밀번호 확인" type="password" autoComplete="new-password"
        error={errors.passwordConfirm?.message} {...register('passwordConfirm')} />

      {signup.isError && <FormError>{signup.error.message}</FormError>}

      <Button type="submit" disabled={signup.isPending}>
        {signup.isPending ? '가입 중...' : '회원가입'}
      </Button>

      <p className="text-center text-xs leading-relaxed text-slate-500">
        회원가입 시{' '}
        <Link href="/terms" className="underline underline-offset-2">
          이용약관
        </Link>
        {' 과 '}
        <Link href="/privacy" className="underline underline-offset-2">
          개인정보처리방침
        </Link>
        {' 에 동의한 것으로 봅니다.'}
      </p>

      <p className="text-center text-sm text-slate-500">
        이미 계정이 있으신가요?{' '}
        <Link href="/login" className="font-medium text-slate-900 underline dark:text-slate-100">
          로그인
        </Link>
      </p>
    </form>
  );
}
