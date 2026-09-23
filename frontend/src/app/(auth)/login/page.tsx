'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/Button';
import { LogoBlock } from '@/components/ui/Logo';
import { FormError, FormField } from '@/components/ui/FormField';
import { useLogin } from '@/hooks/useAuth';
import { SocialButton } from '@/components/ui/SocialButton';
import { isConfigured } from '@/lib/auth/oauth';
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
      <LogoBlock />
      {searchParams.get('withdraw') === 'success' && (
        <p className="rounded-md bg-slate-100 px-3 py-2 text-sm text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          탈퇴가 완료되었습니다. 그동안 이용해주셔서 감사합니다.
        </p>
      )}
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

      <div className="flex items-center gap-3 py-1">
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
        <span className="text-xs text-slate-400">또는</span>
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
      </div>

      <SocialButton provider="kakao" disabled={!isConfigured('kakao')} />
      <SocialButton provider="naver" disabled={!isConfigured('naver')} />
      {(!isConfigured('kakao') || !isConfigured('naver')) && (
        <p className="text-center text-xs text-slate-400">
          소셜 로그인 키가 설정되지 않았습니다 (.env.local)
        </p>
      )}

      <p className="text-center text-sm text-slate-500">
        계정이 없으신가요?{' '}
        <Link href="/signup" className="font-medium text-slate-900 underline dark:text-slate-100">
          회원가입
        </Link>
      </p>

      <p className="text-center text-xs text-slate-400">
        <Link href="/terms" className="underline underline-offset-2">
          이용약관
        </Link>
        <span className="px-1.5">·</span>
        <Link href="/privacy" className="underline underline-offset-2">
          개인정보처리방침
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
