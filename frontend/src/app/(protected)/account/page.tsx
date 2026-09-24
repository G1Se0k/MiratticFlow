'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { useMe, useUpdateMe, useWithdraw } from '@/hooks/useAuth';
import type { UpdateMePayload } from '@/lib/api/auth';
import type { UserResponse } from '@/lib/api/types';

const PROVIDER_LABEL = {
  LOCAL: '이메일 · 비밀번호',
  KAKAO: '카카오',
  NAVER: '네이버',
} as const;

/** 되돌릴 수 없는 동작이라 한 번 더 확인한다. 버튼 하나로 지워지지 않게 한다. */
const CONFIRM_WORD = '탈퇴';

export default function AccountPage() {
  const { data: user } = useMe();
  const withdraw = useWithdraw();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirmWord, setConfirmWord] = useState('');
  const [password, setPassword] = useState('');

  if (!user) return null;

  const needsPassword = user.provider === 'LOCAL';
  const canSubmit = confirmWord === CONFIRM_WORD && (!needsPassword || password.length > 0);

  const close = () => {
    setOpen(false);
    setConfirmWord('');
    setPassword('');
    withdraw.reset();
  };

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">계정</h1>
        <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
          수정
        </Button>
      </div>

      <dl className="divide-y divide-slate-200 border-y border-slate-200 text-sm dark:divide-slate-800 dark:border-slate-800">
        <div className="flex justify-between py-3">
          <dt className="text-slate-500">이름</dt>
          <dd>{user.name}</dd>
        </div>
        <div className="flex justify-between py-3">
          <dt className="text-slate-500">이메일</dt>
          <dd>{user.email ?? '등록되지 않음'}</dd>
        </div>
        <div className="flex justify-between py-3">
          <dt className="text-slate-500">로그인 방식</dt>
          <dd>{PROVIDER_LABEL[user.provider]}</dd>
        </div>
      </dl>

      {editing && <EditProfileModal user={user} onClose={() => setEditing(false)} />}

      <section className="rounded-lg border border-red-200 p-4 dark:border-red-900">
        <h2 className="text-sm font-semibold text-red-600">회원 탈퇴</h2>
        <p className="mt-2 text-sm text-slate-500">
          이메일 · 이름 · 비밀번호와 소셜 로그인 정보가 삭제되고, 참여 중인 워크스페이스와 프로젝트에서 빠집니다.
          이미 작성한 이슈 · 댓글 · 채팅은 팀의 기록이라 남으며 작성자가 &lsquo;탈퇴한 사용자&rsquo;로 표시됩니다.
          자세한 내용은 <Link href="/privacy" className="underline">개인정보처리방침</Link> 을 참고해주세요.
        </p>
        <p className="mt-2 text-sm text-slate-500">되돌릴 수 없습니다.</p>
        <Button variant="danger" size="sm" className="mt-3" onClick={() => setOpen(true)}>
          회원 탈퇴
        </Button>
      </section>

      <Modal open={open} onClose={close} title="정말 탈퇴하시겠어요?">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-slate-500">
            확인을 위해 <b className="text-slate-900 dark:text-slate-100">{CONFIRM_WORD}</b> 를 입력해주세요.
          </p>

          <FormField
            label={`"${CONFIRM_WORD}" 입력`}
            value={confirmWord}
            onChange={(e) => setConfirmWord(e.target.value)}
          />

          {needsPassword && (
            <FormField
              label="비밀번호"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}

          {withdraw.isError && <FormError>{withdraw.error.message}</FormError>}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" size="sm" onClick={close}>
              취소
            </Button>
            <Button
              variant="danger"
              size="sm"
              disabled={!canSubmit || withdraw.isPending}
              onClick={() => withdraw.mutate(needsPassword ? password : null)}
            >
              {withdraw.isPending ? '처리 중...' : '탈퇴하기'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

interface ProfileForm {
  name: string;
  email: string;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/**
 * 계정 정보 수정.
 *
 * 열릴 때만 렌더되므로 입력값(특히 비밀번호)이 닫은 뒤 남지 않는다 — 별도의 reset 이 필요 없다.
 * 소셜 회원은 이메일과 비밀번호가 제공자 쪽 정보이고 확인할 비밀번호도 없어 이름만 바꿀 수 있다.
 */
function EditProfileModal({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const update = useUpdateMe();
  const toast = useToast();
  const canChangeCredentials = user.provider === 'LOCAL';

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ProfileForm>({
    defaultValues: {
      name: user.name,
      email: user.email ?? '',
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    },
  });

  const newPassword = watch('newPassword');
  const emailChanged = canChangeCredentials && watch('email').trim() !== (user.email ?? '');
  // 로그인 수단을 바꿀 때만 현재 비밀번호를 받는다. 이름만 바꾸는 데 비밀번호를 묻는 화면은 쓰기 싫어진다.
  const needsCurrentPassword = emailChanged || newPassword.length > 0;

  const submit = handleSubmit((values) => {
    const payload: UpdateMePayload = {};
    if (values.name.trim() !== user.name) payload.name = values.name.trim();
    if (emailChanged) payload.email = values.email.trim();
    if (canChangeCredentials && values.newPassword) payload.newPassword = values.newPassword;
    if (payload.email || payload.newPassword) payload.currentPassword = values.currentPassword;

    // 아무것도 안 바꾸고 저장을 누르면 요청하지 않는다.
    if (Object.keys(payload).length === 0) {
      onClose();
      return;
    }

    update.mutate(payload, {
      onSuccess: () => {
        // 비밀번호를 바꿨으면 훅이 로그인 화면으로 보내므로 이 토스트는 보이지 않는다.
        if (!payload.newPassword) toast('계정 정보를 수정했습니다.');
        onClose();
      },
    });
  });

  return (
    <Modal open onClose={onClose} title="계정 정보 수정">
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <FormField
          label="이름"
          autoComplete="nickname"
          error={errors.name?.message}
          {...register('name', {
            required: '이름을 입력해주세요.',
            maxLength: { value: 50, message: '50자 이하로 입력해주세요.' },
          })}
        />

        {canChangeCredentials ? (
          <>
            <FormField
              label="이메일"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email', {
                required: '이메일을 입력해주세요.',
                pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '이메일 형식이 올바르지 않습니다.' },
              })}
            />
            <FormField
              label="새 비밀번호 (바꾸지 않으려면 비워두세요)"
              type="password"
              autoComplete="new-password"
              error={errors.newPassword?.message}
              {...register('newPassword', {
                minLength: { value: 8, message: '비밀번호는 8자 이상이어야 합니다.' },
              })}
            />
            {newPassword.length > 0 && (
              <FormField
                label="새 비밀번호 확인"
                type="password"
                autoComplete="new-password"
                error={errors.confirmPassword?.message}
                {...register('confirmPassword', {
                  validate: (value) => value === newPassword || '비밀번호가 일치하지 않습니다.',
                })}
              />
            )}
            {needsCurrentPassword && (
              <FormField
                label="현재 비밀번호"
                type="password"
                autoComplete="current-password"
                error={errors.currentPassword?.message}
                {...register('currentPassword', {
                  validate: (value) =>
                    !needsCurrentPassword || value.length > 0 || '현재 비밀번호를 입력해주세요.',
                })}
              />
            )}
            {newPassword.length > 0 && (
              <p className="text-xs text-slate-500">
                비밀번호를 바꾸면 모든 기기에서 로그아웃되고 다시 로그인해야 합니다.
              </p>
            )}
          </>
        ) : (
          <p className="text-xs text-slate-500">
            {PROVIDER_LABEL[user.provider]} 로 로그인한 계정은 이메일과 비밀번호를 여기서 바꿀 수 없습니다.
          </p>
        )}

        {update.isError && <FormError>{update.error.message}</FormError>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={update.isPending}>
            {update.isPending ? '저장 중...' : '저장'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
