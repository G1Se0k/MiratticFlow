'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal, ModalActions } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { useSearchParams } from 'next/navigation';
import { useMe, useUpdateName } from '@/hooks/useAuth';
import { api, withdrawUrl } from '@/lib/api/client';
import type { UserResponse } from '@/lib/api/types';

/** 이메일 · 비밀번호 · 로그인 방법(카카오 · 네이버)은 Mirattic 계정에서 관리한다. */
const MIRATTIC_ACCOUNT_URL = process.env.NEXT_PUBLIC_AUTH_ACCOUNT_URL ?? 'https://auth.mirattic.com/account';

/** 되돌릴 수 없는 동작이라 한 번 더 확인한다. 버튼 하나로 지워지지 않게 한다. */
const CONFIRM_WORD = '탈퇴';

/** 탈퇴 확인(Auth 재로그인)이 막혔을 때 돌아오는 사유. */
const WITHDRAW_ERRORS: Record<string, string> = {
  blocked: '혼자 관리자인 워크스페이스가 있어 탈퇴할 수 없습니다. 다른 멤버를 관리자로 지정하거나 워크스페이스를 삭제해주세요.',
  mismatch: '지금 로그인한 계정으로 다시 로그인해야 탈퇴할 수 있습니다.',
  expired: '로그인이 만료되었습니다. 다시 시도해주세요.',
  failed: '탈퇴를 확인하지 못했습니다. 다시 시도해주세요.',
};

export default function AccountPage() {
  const { data: user } = useMe();
  const withdrawError = useSearchParams().get('withdraw');
  const [open, setOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirmWord, setConfirmWord] = useState('');

  if (!user) return null;

  const close = () => {
    setOpen(false);
    setConfirmWord('');
  };

  // Auth 에서 비밀번호를 다시 입력하러 간다. 먼저 API 를 한 번 불러 로그인 쿠키가 만료됐으면 재발급받는다.
  const startWithdrawal = async () => {
    setLeaving(true);
    await api.get('/api/users/me').catch(() => {});
    window.location.href = withdrawUrl();
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
          이름 수정
        </Button>
      </header>

      <dl className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface text-[13px]">
        <Row label="이름">{user.name}</Row>
        <Row label="이메일">{user.email ?? <span className="text-ink-faint">등록되지 않음</span>}</Row>
      </dl>

      <section className="flex flex-col gap-2 rounded-card border border-line bg-surface p-4">
        <h2 className="text-[13px] font-semibold">Mirattic 계정</h2>
        <p className="text-[13px] leading-relaxed text-ink-soft">
          이메일 · 비밀번호 · 로그인 방법은 Mirattic Sync 와 함께 쓰는 Mirattic 계정에서 바꿀 수 있습니다.
        </p>
        <a
          href={MIRATTIC_ACCOUNT_URL}
          target="_blank"
          rel="noreferrer"
          className="mt-1 self-start text-[13px] font-medium text-accent hover:underline"
        >
          Mirattic 계정 관리 ↗
        </a>
      </section>

      {editing && <EditNameModal user={user} onClose={() => setEditing(false)} />}

      <section className="flex flex-col gap-2 rounded-card border border-line bg-surface p-4">
        <h2 className="text-[13px] font-semibold">회원 탈퇴</h2>
        <p className="text-[13px] leading-relaxed text-ink-soft">
          Flow 의 이름 · 이메일 정보가 삭제되고, 참여 중인 워크스페이스와 프로젝트에서 빠집니다.
          이미 작성한 이슈 · 댓글 · 채팅은 팀의 기록이라 남으며 작성자가 &lsquo;탈퇴한 사용자&rsquo;로 표시됩니다.
          Mirattic 계정은 남습니다. 되돌릴 수 없습니다. 자세한 내용은{' '}
          <Link href="/privacy" className="text-accent hover:underline">
            개인정보처리방침
          </Link>
          을 참고해주세요.
        </p>
        {withdrawError && <FormError>{WITHDRAW_ERRORS[withdrawError] ?? WITHDRAW_ERRORS.failed}</FormError>}
        <Button variant="danger" size="sm" className="mt-1 self-start" onClick={() => setOpen(true)}>
          회원 탈퇴
        </Button>
      </section>

      <Modal
        open={open}
        onClose={close}
        title="정말 탈퇴하시겠어요?"
        description="Flow 의 계정 정보가 삭제되고 되돌릴 수 없습니다. 확인을 위해 Mirattic 계정으로 한 번 더 로그인합니다."
      >
        <div className="flex flex-col gap-3.5">
          <FormField
            label={`확인을 위해 "${CONFIRM_WORD}" 를 입력해주세요`}
            placeholder={CONFIRM_WORD}
            value={confirmWord}
            onChange={(e) => setConfirmWord(e.target.value)}
          />

          <ModalActions>
            <Button variant="ghost" size="sm" onClick={close}>
              취소
            </Button>
            <Button
              variant="dangerSolid"
              size="sm"
              disabled={confirmWord !== CONFIRM_WORD || leaving}
              onClick={startWithdrawal}
            >
              {leaving ? '이동 중...' : '로그인하고 탈퇴하기'}
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

/** 이름 변경. 열릴 때만 렌더되므로 닫으면 입력값이 남지 않는다. */
function EditNameModal({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const update = useUpdateName();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<{ name: string }>({ defaultValues: { name: user.name } });

  const submit = handleSubmit(({ name }) => {
    // 아무것도 안 바꾸고 저장을 누르면 요청하지 않는다.
    if (name.trim() === user.name) {
      onClose();
      return;
    }
    update.mutate(name.trim(), {
      onSuccess: () => {
        toast('이름을 바꿨습니다.');
        onClose();
      },
    });
  });

  return (
    <Modal open onClose={onClose} title="이름 수정">
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
