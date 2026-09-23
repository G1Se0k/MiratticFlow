'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { useMe, useWithdraw } from '@/hooks/useAuth';

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
      <h1 className="text-xl font-semibold">계정</h1>

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
