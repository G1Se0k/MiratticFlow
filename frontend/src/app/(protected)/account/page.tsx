'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal, ModalActions } from '@/components/ui/Modal';
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
    <div className="flex max-w-2xl flex-col gap-7">
      <header className="flex items-center gap-3">
        <Avatar name={user.name} />
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-[17px] font-semibold">{user.name}</h1>
          <p className="truncate text-[13px] text-ink-soft">{user.email ?? '이메일이 등록되지 않았습니다'}</p>
        </div>
        <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
          수정
        </Button>
      </header>

      <dl className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface text-[13px]">
        <Row label="이름">{user.name}</Row>
        <Row label="이메일">{user.email ?? <span className="text-ink-faint">등록되지 않음</span>}</Row>
        <Row label="로그인 방식">{PROVIDER_LABEL[user.provider]}</Row>
      </dl>

      {editing && <EditProfileModal user={user} onClose={() => setEditing(false)} />}

      <section className="flex flex-col gap-2 rounded-card border border-line bg-surface p-4">
        <h2 className="text-[13px] font-semibold">회원 탈퇴</h2>
        <p className="text-[13px] leading-relaxed text-ink-soft">
          이메일 · 이름 · 비밀번호와 소셜 로그인 정보가 삭제되고, 참여 중인 워크스페이스와 프로젝트에서 빠집니다.
          이미 작성한 이슈 · 댓글 · 채팅은 팀의 기록이라 남으며 작성자가 &lsquo;탈퇴한 사용자&rsquo;로 표시됩니다.
          되돌릴 수 없습니다. 자세한 내용은{' '}
          <Link href="/privacy" className="text-accent hover:underline">
            개인정보처리방침
          </Link>
          을 참고해주세요.
        </p>
        <Button variant="danger" size="sm" className="mt-1 self-start" onClick={() => setOpen(true)}>
          회원 탈퇴
        </Button>
      </section>

      <Modal
        open={open}
        onClose={close}
        title="정말 탈퇴하시겠어요?"
        description="계정 정보가 삭제되고 되돌릴 수 없습니다."
      >
        <div className="flex flex-col gap-3.5">
          <FormField
            label={`확인을 위해 "${CONFIRM_WORD}" 를 입력해주세요`}
            placeholder={CONFIRM_WORD}
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

          <ModalActions>
            <Button variant="ghost" size="sm" onClick={close}>
              취소
            </Button>
            <Button
              variant="dangerSolid"
              size="sm"
              disabled={!canSubmit || withdraw.isPending}
              onClick={() => withdraw.mutate(needsPassword ? password : null)}
            >
              {withdraw.isPending ? '처리 중...' : '탈퇴하기'}
            </Button>
          </ModalActions>
        </div>
      </Modal>
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 px-4 py-2.5">
      <dt className="text-ink-soft">{label}</dt>
      <dd className="min-w-0 truncate">{children}</dd>
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
      <form className="flex flex-col gap-3.5" onSubmit={submit} noValidate>
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
              label="새 비밀번호"
              type="password"
              autoComplete="new-password"
              hint={
                newPassword.length > 0
                  ? '비밀번호를 바꾸면 모든 기기에서 로그아웃됩니다.'
                  : '바꾸지 않으려면 비워두세요.'
              }
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
          </>
        ) : (
          <p className="text-xs leading-relaxed text-ink-faint">
            {PROVIDER_LABEL[user.provider]} 로 로그인한 계정은 이메일과 비밀번호를 여기서 바꿀 수 없습니다.
          </p>
        )}

        {update.isError && <FormError>{update.error.message}</FormError>}

        <ModalActions>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={update.isPending}>
            {update.isPending ? '저장 중...' : '저장'}
          </Button>
        </ModalActions>
      </form>
    </Modal>
  );
}
