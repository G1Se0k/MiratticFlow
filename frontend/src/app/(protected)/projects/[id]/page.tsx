'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { useMe } from '@/hooks/useAuth';
import { ProjectChatDock } from '@/components/chat/ProjectChatDock';
import { ProjectDashboard } from '@/components/dashboard/ProjectDashboard';
import { useProject, useProjectMembers, useProjectMutations } from '@/hooks/useProjects';
import { useMembers, useWorkspace } from '@/hooks/useWorkspaces';
import type { Project, ProjectInput } from '@/lib/api/project';
import { IssueSection } from './IssueSection';

export default function ProjectDetailPage() {
  const projectId = Number(useParams<{ id: string }>().id);
  const router = useRouter();
  const toast = useToast();

  const { data: project, isPending, isError, error } = useProject(projectId);
  const { data: members } = useProjectMembers(projectId);
  const { data: me } = useMe();
  const { data: workspace } = useWorkspace(project?.workspaceId ?? 0);

  const { remove, addMember, removeMember } = useProjectMutations(projectId, project?.workspaceId);
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isPending) return <Spinner />;
  if (isError || !project) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-sm text-slate-500">{error?.message ?? '프로젝트를 불러오지 못했습니다.'}</p>
        <Button variant="secondary" onClick={() => router.push('/workspaces')}>
          워크스페이스 목록으로
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <Link
          href={`/workspaces/${project.workspaceId}`}
          className="w-fit text-xs text-slate-500 hover:text-brand-600 dark:hover:text-brand-300"
        >
          ← {workspace?.name ?? '워크스페이스'}
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-xl font-semibold tracking-tight">{project.name}</h1>
          {project.status === 'ARCHIVED' && (
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-800">
              보관됨
            </span>
          )}
          {project.canManage && (
            <div className="ml-auto flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
                설정
              </Button>
              <Button size="sm" variant="danger" onClick={() => setConfirmDelete(true)}>
                삭제
              </Button>
            </div>
          )}
        </div>
        <p className="text-sm text-slate-500">{project.description || '설명 없음'}</p>
        <p className="text-xs text-slate-400">만든 사람 {project.createdByName}</p>
      </header>

      <ProjectDashboard projectId={projectId} />

      <IssueSection projectId={projectId} members={members ?? []} />

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-medium text-slate-500">참여자 {members?.length ?? 0}명</h2>
          {project.canManage && (
            <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
              참여자 추가
            </Button>
          )}
        </div>
        <ul className="divide-y divide-slate-200 rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {members?.map((member) => (
            <li key={member.userId} className="flex items-center gap-3 px-4 py-3">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-medium text-brand-700 dark:bg-brand-700/25 dark:text-brand-200">
                {member.name.slice(0, 1)}
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">
                  {member.name}
                  {member.userId === me?.id && <span className="ml-1 text-xs text-slate-400">(나)</span>}
                </p>
                <p className="truncate text-xs text-slate-500">{member.email ?? '이메일 없음'}</p>
              </div>
              {project.canManage && (
                <Button
                  size="sm"
                  variant="danger"
                  className="ml-auto shrink-0"
                  onClick={() =>
                    removeMember.mutate(member.userId, {
                      onSuccess: () => toast('참여자를 제외했습니다.'),
                      onError: (e) => toast(e.message, 'error'),
                    })
                  }
                >
                  제외
                </Button>
              )}
            </li>
          ))}
        </ul>
      </section>

      <EditModal open={editing} onClose={() => setEditing(false)} project={project} />

      <Modal open={adding} onClose={() => setAdding(false)} title="참여자 추가">
        <AddMemberList
          workspaceId={project.workspaceId}
          joinedIds={members?.map((m) => m.userId) ?? []}
          onAdd={(userId) =>
            addMember.mutate(userId, {
              onSuccess: () => toast('참여자를 추가했습니다.'),
              onError: (e) => toast(e.message, 'error'),
            })
          }
        />
      </Modal>

      <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} title="프로젝트를 삭제할까요?">
        <p className="text-sm text-slate-500">삭제하면 되돌릴 수 없습니다. 보관만 하려면 설정에서 상태를 바꾸세요.</p>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
            취소
          </Button>
          <Button
            variant="danger"
            disabled={remove.isPending}
            onClick={() =>
              remove.mutate(undefined, {
                onSuccess: () => {
                  toast('프로젝트를 삭제했습니다.');
                  router.push(`/workspaces/${project.workspaceId}`);
                },
                onError: (e) => {
                  setConfirmDelete(false);
                  toast(e.message, 'error');
                },
              })
            }
          >
            삭제
          </Button>
        </div>
      </Modal>

      <ProjectChatDock projectId={projectId} />
    </div>
  );
}

function EditModal({ open, onClose, project }: { open: boolean; onClose: () => void; project: Project }) {
  const { update } = useProjectMutations(project.id, project.workspaceId);
  const toast = useToast();
  const { register, handleSubmit, formState: { errors } } = useForm<ProjectInput>({
    values: { name: project.name, description: project.description ?? '', status: project.status },
  });

  return (
    <Modal open={open} onClose={onClose} title="프로젝트 설정">
      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit((values) =>
          update.mutate(values, {
            onSuccess: () => {
              toast('프로젝트를 수정했습니다.');
              onClose();
            },
          }),
        )}
      >
        <FormField
          label="이름"
          error={errors.name?.message}
          {...register('name', { required: '이름을 입력해주세요.', maxLength: { value: 50, message: '50자 이하' } })}
        />
        <FormField label="설명" {...register('description')} />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="status" className="text-sm font-medium text-slate-700 dark:text-slate-300">
            상태
          </label>
          <select
            id="status"
            {...register('status')}
            className="h-10 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none dark:border-slate-700 dark:bg-slate-900"
          >
            <option value="ACTIVE">진행 중</option>
            <option value="ARCHIVED">보관</option>
          </select>
        </div>
        {update.isError && <FormError>{update.error.message}</FormError>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={update.isPending}>
            저장
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** 워크스페이스 멤버 중 아직 참여하지 않은 사람만 고를 수 있게 한다. */
function AddMemberList({
  workspaceId,
  joinedIds,
  onAdd,
}: {
  workspaceId: number;
  joinedIds: number[];
  onAdd: (userId: number) => void;
}) {
  const { data: workspaceMembers, isPending } = useMembers(workspaceId);
  const candidates = workspaceMembers?.filter((m) => !joinedIds.includes(m.userId)) ?? [];

  if (isPending) return <Spinner />;
  if (candidates.length === 0) {
    return <p className="text-sm text-slate-500">워크스페이스 멤버가 모두 참여 중입니다.</p>;
  }

  return (
    <ul className="flex max-h-72 flex-col gap-1 overflow-y-auto">
      {candidates.map((member) => (
        <li key={member.userId} className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-slate-50 dark:hover:bg-slate-800">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{member.name}</p>
            <p className="truncate text-xs text-slate-500">{member.email ?? '이메일 없음'}</p>
          </div>
          <Button size="sm" variant="secondary" className="ml-auto shrink-0" onClick={() => onAdd(member.userId)}>
            추가
          </Button>
        </li>
      ))}
    </ul>
  );
}
