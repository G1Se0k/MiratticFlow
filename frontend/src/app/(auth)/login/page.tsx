'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/Button';
import { FormError, FormField } from '@/components/ui/FormField';
import { useLogin } from '@/hooks/useAuth';
import { loginSchema, type LoginForm } from '@/lib/validation/auth';

function LoginForm() {
  const searchParams = useSearchParams();
  const login = useLogin();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  return (
    <form onSubmit={handleSubmit((values) => login.mutate(values))} className="flex flex-col gap-4" noValidate>
      {searchParams.get('signup') === 'success' && (
        <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
          가입이 완료되었습니다. 로그인해주세요.
        </p>
      )}

      <FormField label="이메일" type="email" autoComplete="email" placeholder="you@example.com"
        error={errors.email?.message} {...register('email')} />
      <FormField label="비밀번호" type="password" autoComplete="current-password"
        error={errors.password?.message} {...register('password')} />

      {/* 서버가 내려준 메시지를 그대로 보여준다 (예: 이메일 또는 비밀번호가 올바르지 않습니다) */}
      {login.isError && <FormError>{login.error.message}</FormError>}

      <Button type="submit" disabled={login.isPending}>
        {login.isPending ? '로그인 중...' : '로그인'}
      </Button>

      <p className="text-center text-sm text-slate-500">
        계정이 없으신가요?{' '}
        <Link href="/signup" className="font-medium text-slate-900 underline dark:text-slate-100">
          회원가입
        </Link>
      </p>
    </form>
  );
}

export default function LoginPage() {
  // useSearchParams 는 CSR 경계가 필요하다.
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}
