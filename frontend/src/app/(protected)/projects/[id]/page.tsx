'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { FormError, FormField } from '@/components/ui/FormField';
import { Icon } from '@/components/ui/Icon';
import { Menu } from '@/components/ui/Menu';
import { Modal, ModalActions } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
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

  if (isPending) return <ProjectPageSkeleton />;
  if (isError || !project) {
    return (
      <EmptyState
        icon="alert"
        title={error?.message ?? '프로젝트를 불러오지 못했습니다.'}
        action={
          <Button variant="secondary" size="sm" onClick={() => router.push('/workspaces')}>
            워크스페이스 목록으로
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-7">
      <header className="flex flex-col gap-1.5">
        <Link
          href={`/workspaces/${project.workspaceId}`}
          className="flex w-fit items-center gap-1 text-xs text-ink-faint transition-colors hover:text-ink"
        >
          <Icon name="arrowLeft" className="size-3.5" />
          {workspace?.name ?? '워크스페이스'}
        </Link>
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <h1 className="truncate text-[17px] font-semibold">{project.name}</h1>
              {project.status === 'ARCHIVED' && <Badge>보관됨</Badge>}
            </div>
            <p className="mt-0.5 text-[13px] text-ink-soft">
              {project.description || '설명 없음'} · 만든 사람 {project.createdByName}
            </p>
          </div>
          {project.canManage && (
            <>
              <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
                <Icon name="settings" className="size-3.5" />
                설정
              </Button>
              <Menu
                items={[{ label: '프로젝트 삭제', icon: 'trash', onSelect: () => setConfirmDelete(true), danger: true }]}
              />
            </>
          )}
        </div>
      </header>

      <ProjectDashboard projectId={projectId} />

      <IssueSection projectId={projectId} members={members ?? []} />

      <section className="flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <h2 className="text-[13px] font-semibold">참여자 {members?.length ?? 0}</h2>
          {project.canManage && (
            <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
              <Icon name="plus" className="size-3.5" />
              참여자 추가
            </Button>
          )}
        </div>
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {members?.map((member) => (
            <li key={member.userId} className="flex items-center gap-3 px-4 py-2.5">
              <Avatar name={member.name} />
              <div className="min-w-0">
                <p className="truncate text-[13px] font-medium">
                  {member.name}
                  {member.userId === me?.id && (
                    <span className="ml-1 text-xs font-normal text-ink-faint">(나)</span>
                  )}
                </p>
                <p className="truncate text-xs text-ink-soft">{member.email ?? '이메일 없음'}</p>
              </div>
              {project.canManage && (
                <Button
                  size="icon"
                  variant="danger"
                  aria-label={`${member.name} 제외`}
                  className="ml-auto"
                  onClick={() =>
                    removeMember.mutate(member.userId, {
                      onSuccess: () => toast('참여자를 제외했습니다.'),
                      onError: (e) => toast(e.message, 'error'),
                    })
                  }
                >
                  <Icon name="close" className="size-3.5" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      </section>

      <EditModal open={editing} onClose={() => setEditing(false)} project={project} />

      <Modal open={adding} onClose={() => setAdding(false)} title="참여자 추가" description="워크스페이스 멤버 중에서 고릅니다.">
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

      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="프로젝트를 삭제할까요?"
        description="삭제하면 되돌릴 수 없습니다. 보관만 하려면 설정에서 상태를 바꾸세요."
      >
        <ModalActions>
          <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>
            취소
          </Button>
          <Button
            variant="dangerSolid"
            size="sm"
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
        </ModalActions>
      </Modal>

      <ProjectChatDock projectId={projectId} />
    </div>
  );
}

/**
 * 화면 전체를 가리는 스피너 대신 실제 배치와 같은 모양을 먼저 그린다.
 * 페이지 최상단의 isPending 이 모든 섹션을 막고 있어서, 각 섹션에 스켈레톤을 넣어도
 * 여기까지 오지 않으면 보이지 않는다 — 게이트 자체를 바꿔야 효과가 있다.
 */
function ProjectPageSkeleton() {
  return (
    <div className="flex flex-col gap-7" role="status" aria-label="불러오는 중">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-5 w-48" />
        <Skeleton className="h-3.5 w-64" />
      </div>
      <Skeleton className="h-[70px]" />
      <div className="grid gap-3 lg:grid-cols-3">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-[230px]" />
        ))}
      </div>
      <Skeleton className="h-48" />
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
        className="flex flex-col gap-3.5"
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
        <Select label="상태" {...register('status')}>
          <option value="ACTIVE">진행 중</option>
          <option value="ARCHIVED">보관</option>
        </Select>
        {update.isError && <FormError>{update.error.message}</FormError>}
        <ModalActions>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={update.isPending}>
            저장
          </Button>
        </ModalActions>
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

  if (isPending) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-10" />
        ))}
      </div>
    );
  }
  if (candidates.length === 0) {
    return <p className="text-[13px] text-ink-soft">워크스페이스 멤버가 모두 참여 중입니다.</p>;
  }

  return (
    <ul className="thin-scroll flex max-h-72 flex-col gap-0.5 overflow-y-auto">
      {candidates.map((member) => (
        <li key={member.userId} className="flex items-center gap-2.5 rounded-md px-1.5 py-1.5 hover:bg-raised">
          <Avatar name={member.name} size="sm" />
          <div className="min-w-0">
            <p className="truncate text-[13px] font-medium">{member.name}</p>
            <p className="truncate text-xs text-ink-soft">{member.email ?? '이메일 없음'}</p>
          </div>
          <Button size="sm" variant="secondary" className="ml-auto shrink-0" onClick={() => onAdd(member.userId)}>
            추가
          </Button>
        </li>
      ))}
    </ul>
  );
}
