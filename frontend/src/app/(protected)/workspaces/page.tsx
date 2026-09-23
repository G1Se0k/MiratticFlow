'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { SkeletonList } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { useCreateWorkspace, useWorkspaces } from '@/hooks/useWorkspaces';
import { workspaceApi } from '@/lib/api/workspace';

export default function WorkspacesPage() {
  const { data: workspaces, isPending } = useWorkspaces();
  const [creating, setCreating] = useState(false);
  const [joining, setJoining] = useState(false);

  if (isPending) return <SkeletonList rows={3} className="h-24" />;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight">워크스페이스</h1>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setJoining(true)}>
            코드로 참여
          </Button>
          <Button onClick={() => setCreating(true)}>새 워크스페이스</Button>
        </div>
      </div>

      {workspaces?.length === 0 ? (
        <EmptyState
          title="아직 워크스페이스가 없습니다"
          description="새로 만들거나, 팀에서 받은 초대 코드로 참여하세요."
          action={<Button onClick={() => setCreating(true)}>새 워크스페이스 만들기</Button>}
        />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {workspaces?.map((workspace) => (
            <li key={workspace.id}>
              <Link
                href={`/workspaces/${workspace.id}`}
                className="block rounded-xl border border-slate-200 p-5 transition-colors hover:border-brand-400 dark:border-slate-800 dark:hover:border-brand-500"
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="font-medium">{workspace.name}</p>
                  {workspace.myRole === 'OWNER' && (
                    <span className="rounded bg-brand-50 px-1.5 py-0.5 text-xs text-brand-700 dark:bg-brand-700/20 dark:text-brand-200">
                      관리자
                    </span>
                  )}
                </div>
                <p className="mt-1 line-clamp-2 text-sm text-slate-500">
                  {workspace.description || '설명 없음'}
                </p>
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
        className="flex flex-col gap-4"
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
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={create.isPending}>
            만들기
          </Button>
        </div>
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
    <Modal open={open} onClose={onClose} title="코드로 참여">
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <FormField
          label="초대 코드"
          placeholder="ABCD-1234"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          error={error ?? undefined}
          autoFocus
        />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={pending || code.trim().length < 4}>
            참여하기
          </Button>
        </div>
      </form>
    </Modal>
  );
}
