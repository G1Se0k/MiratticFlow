'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { FormError, FormField } from '@/components/ui/FormField';
import { Icon } from '@/components/ui/Icon';
import { Modal, ModalActions } from '@/components/ui/Modal';
import { SkeletonList } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { useCreateWorkspace, useWorkspaces } from '@/hooks/useWorkspaces';
import { workspaceApi } from '@/lib/api/workspace';

export default function WorkspacesPage() {
  const { data: workspaces, isPending } = useWorkspaces();
  const [creating, setCreating] = useState(false);
  const [joining, setJoining] = useState(false);

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-wrap items-center gap-2">
        <div className="min-w-0 flex-1">
          <h1 className="text-[17px] font-semibold">워크스페이스</h1>
          <p className="mt-0.5 text-[13px] text-ink-soft">참여 중인 팀 공간입니다.</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => setJoining(true)}>
          코드로 참여
        </Button>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Icon name="plus" className="size-3.5" />
          새 워크스페이스
        </Button>
      </header>

      {isPending ? (
        <SkeletonList rows={3} className="h-16" />
      ) : workspaces?.length === 0 ? (
        <div className="rounded-card border border-line bg-surface">
          <EmptyState
            icon="layers"
            title="아직 워크스페이스가 없습니다"
            description="새로 만들거나, 팀에서 받은 초대 코드로 참여하세요."
            action={
              <Button size="sm" onClick={() => setCreating(true)}>
                새 워크스페이스 만들기
              </Button>
            }
          />
        </div>
      ) : (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {workspaces?.map((workspace) => (
            <li key={workspace.id}>
              <Link
                href={`/workspaces/${workspace.id}`}
                className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-raised"
              >
                <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-raised text-ink-soft">
                  <Icon name="layers" className="size-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <span className="truncate text-[13px] font-medium">{workspace.name}</span>
                    {workspace.myRole === 'OWNER' && <Badge>관리자</Badge>}
                  </span>
                  <span className="mt-0.5 block truncate text-[13px] text-ink-soft">
                    {workspace.description || '설명 없음'}
                  </span>
                </span>
                <Icon
                  name="chevronRight"
                  className="size-4 text-ink-faint transition-transform group-hover:translate-x-0.5"
                />
              </Link>
            </li>
          ))}
        </ul>
      )}

      <CreateModal open={creating} onClose={() => setCreating(false)} />
      <JoinModal open={joining} onClose={() => setJoining(false)} />
    </div>
  );
}

function CreateModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const create = useCreateWorkspace();
  const router = useRouter();
  const { register, handleSubmit, reset, formState: { errors } } =
    useForm<{ name: string; description?: string }>();

  return (
    <Modal open={open} onClose={onClose} title="새 워크스페이스">
      <form
        className="flex flex-col gap-3.5"
        onSubmit={handleSubmit((values) =>
          create.mutate(values, {
            onSuccess: (workspace) => {
              reset();
              onClose();
              router.push(`/workspaces/${workspace.id}`);
            },
          }),
        )}
      >
        <FormField
          label="이름"
          placeholder="예: 미라틱 팀"
          error={errors.name?.message}
          {...register('name', { required: '이름을 입력해주세요.', maxLength: { value: 50, message: '50자 이하' } })}
        />
        <FormField label="설명 (선택)" placeholder="무엇을 하는 팀인가요?" {...register('description')} />
        {create.isError && <FormError>{create.error.message}</FormError>}
        <ModalActions>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={create.isPending}>
            만들기
          </Button>
        </ModalActions>
      </form>
    </Modal>
  );
}

/** 상시 코드를 직접 입력해 참여한다. 링크를 받지 못한 사람을 위한 경로다. */
function JoinModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const toast = useToast();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const { workspaceId } = await workspaceApi.acceptInvite(code.trim().toUpperCase());
      toast('워크스페이스에 참여했습니다.');
      onClose();
      router.push(`/workspaces/${workspaceId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '참여하지 못했습니다.');
    } finally {
      setPending(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="코드로 참여" description="팀에서 받은 참여 코드를 입력하세요.">
      <form className="flex flex-col gap-3.5" onSubmit={submit}>
        <FormField
          label="초대 코드"
          placeholder="ABCD-1234"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          error={error ?? undefined}
          autoFocus
          className="font-mono tracking-widest"
        />
        <ModalActions>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={pending || code.trim().length < 4}>
            참여하기
          </Button>
        </ModalActions>
      </form>
    </Modal>
  );
}
