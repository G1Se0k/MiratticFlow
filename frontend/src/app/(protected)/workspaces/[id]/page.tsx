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
import { useCreateProject, useProjects } from '@/hooks/useProjects';
import {
  useInviteLinks,
  useUpdateWorkspace,
  useInviteMutations,
  useJoinCode,
  useMemberMutations,
  useMembers,
  useWorkspace,
} from '@/hooks/useWorkspaces';
import type { ProjectInput } from '@/lib/api/project';
import { workspaceApi, type Member, type Workspace } from '@/lib/api/workspace';

export default function WorkspaceDetailPage() {
  const params = useParams<{ id: string }>();
  const workspaceId = Number(params.id);
  const router = useRouter();
  const toast = useToast();

  const { data: workspace, isPending, isError, error } = useWorkspace(workspaceId);
  const { data: members } = useMembers(workspaceId);
  const { data: me } = useMe();

  const isOwner = workspace?.myRole === 'OWNER';
  const { data: joinCode } = useJoinCode(workspaceId, Boolean(isOwner));
  const { data: links } = useInviteLinks(workspaceId, Boolean(isOwner));

  const { changeRole, removeMember } = useMemberMutations(workspaceId);
  const { createLink, revokeLink, regenerateCode } = useInviteMutations(workspaceId);
  const { data: projects } = useProjects(workspaceId);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [creatingProject, setCreatingProject] = useState(false);
  const [editingWorkspace, setEditingWorkspace] = useState(false);

  if (isPending) return <Spinner />;
  if (isError || !workspace) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-sm text-slate-500">{error?.message ?? '워크스페이스를 불러오지 못했습니다.'}</p>
        <Button variant="secondary" onClick={() => router.push('/workspaces')}>
          목록으로
        </Button>
      </div>
    );
  }

  const copy = async (text: string, label: string) => {
    await navigator.clipboard.writeText(text);
    toast(`${label}를 복사했습니다.`);
  };

  const inviteUrl = (code: string) => `${window.location.origin}/invite/${code}`;

  return (
    <div className="flex flex-col gap-8">
      <header className="flex items-start gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">{workspace.name}</h1>
          {workspace.description && <p className="mt-1 text-sm text-slate-500">{workspace.description}</p>}
        </div>
        {isOwner && (
          <Button size="sm" variant="secondary" className="ml-auto shrink-0" onClick={() => setEditingWorkspace(true)}>
            설정
          </Button>
        )}
      </header>

      {/* 프로젝트 — 워크스페이스에서 가장 자주 쓰는 화면이라 맨 위에 둔다 */}
      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-medium text-slate-500">프로젝트 {projects?.length ?? 0}개</h2>
          <Button size="sm" onClick={() => setCreatingProject(true)}>
            새 프로젝트
          </Button>
        </div>
        {projects?.length === 0 ? (
          <EmptyState
            title="아직 프로젝트가 없습니다"
            description="프로젝트를 만들면 그 안에서 이슈를 관리하고 팀과 이야기할 수 있습니다."
            action={<Button onClick={() => setCreatingProject(true)}>새 프로젝트 만들기</Button>}
          />
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {projects?.map((project) => (
              <li key={project.id}>
                <Link
                  href={`/projects/${project.id}`}
                  className="block rounded-xl border border-slate-200 p-4 transition-colors hover:border-brand-400 dark:border-slate-800 dark:hover:border-brand-500"
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="truncate font-medium">{project.name}</p>
                    {project.status === 'ARCHIVED' && (
                      <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-800">
                        보관됨
                      </span>
                    )}
                  </div>
                  <p className="mt-1 line-clamp-2 text-sm text-slate-500">{project.description || '설명 없음'}</p>
                  <p className="mt-2 text-xs text-slate-400">
                    참여자 {project.memberCount}명 · {project.createdByName}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {isOwner && (
        <section className="flex flex-col gap-4 rounded-xl border border-slate-200 p-5 dark:border-slate-800">
          <h2 className="text-sm font-medium text-slate-500">멤버 초대</h2>

          {/* 상시 코드 — 아는 사람은 누구나 참여 */}
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <p className="text-xs text-slate-500">참여 코드</p>
              <p className="font-mono text-lg tracking-widest">{joinCode?.code ?? '···'}</p>
            </div>
            <div className="ml-auto flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => joinCode && copy(joinCode.code, '코드')}>
                코드 복사
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() =>
                  regenerateCode.mutate(undefined, { onSuccess: () => toast('새 코드를 발급했습니다. 이전 코드는 사용할 수 없습니다.') })
                }
                disabled={regenerateCode.isPending}
              >
                재발급
              </Button>
            </div>
          </div>

          <hr className="border-slate-200 dark:border-slate-800" />

          {/* 일회용 링크 — 특정인에게 전달 */}
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">초대 링크</p>
              <p className="text-xs text-slate-500">한 번만 사용할 수 있고 7일 뒤 만료됩니다.</p>
            </div>
            <Button
              size="sm"
              onClick={() =>
                createLink.mutate(undefined, {
                  onSuccess: (invite) => copy(inviteUrl(invite.code), '초대 링크'),
                })
              }
              disabled={createLink.isPending}
            >
              링크 만들기
            </Button>
          </div>

          {links && links.length > 0 && (
            <ul className="flex flex-col gap-2">
              {links.map((link) => (
                <li
                  key={link.id}
                  className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm dark:bg-slate-900"
                >
                  <span className="truncate font-mono text-xs text-slate-500">{inviteUrl(link.code)}</span>
                  <span className="ml-auto shrink-0 text-xs text-slate-400">
                    {link.usedCount}/{link.maxUses}
                  </span>
                  <Button size="sm" variant="ghost" onClick={() => copy(inviteUrl(link.code), '초대 링크')}>
                    복사
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => revokeLink.mutate(link.id, { onSuccess: () => toast('링크를 폐기했습니다.') })}
                  >
                    폐기
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-slate-500">멤버 {members?.length ?? 0}명</h2>
        <ul className="divide-y divide-slate-200 rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {members?.map((member) => (
            <MemberRow
              key={member.userId}
              member={member}
              isMe={member.userId === me?.id}
              canManage={Boolean(isOwner) && member.userId !== me?.id}
              onChangeRole={(role) =>
                changeRole.mutate(
                  { userId: member.userId, role },
                  {
                    onSuccess: () => toast('역할을 변경했습니다.'),
                    onError: (e) => toast(e.message, 'error'),
                  },
                )
              }
              onRemove={() =>
                removeMember.mutate(member.userId, {
                  onSuccess: () => toast('멤버를 제외했습니다.'),
                  onError: (e) => toast(e.message, 'error'),
                })
              }
            />
          ))}
        </ul>
      </section>

      <section>
        <Button variant="danger" size="sm" onClick={() => setConfirmLeave(true)}>
          워크스페이스 나가기
        </Button>
      </section>

      <CreateProjectModal
        open={creatingProject}
        onClose={() => setCreatingProject(false)}
        workspaceId={workspaceId}
      />

      <WorkspaceSettingsModal
        open={editingWorkspace}
        onClose={() => setEditingWorkspace(false)}
        workspace={workspace}
      />

      <Modal open={confirmLeave} onClose={() => setConfirmLeave(false)} title="워크스페이스를 나갈까요?">
        <p className="text-sm text-slate-500">나가면 이 워크스페이스의 내용을 볼 수 없습니다.</p>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirmLeave(false)}>
            취소
          </Button>
          <Button
            variant="danger"
            onClick={async () => {
              try {
                await workspaceApi.leave(workspaceId);
                toast('워크스페이스를 나갔습니다.');
                router.push('/workspaces');
              } catch (e) {
                setConfirmLeave(false);
                toast(e instanceof Error ? e.message : '나가지 못했습니다.', 'error');
              }
            }}
          >
            나가기
          </Button>
        </div>
      </Modal>
    </div>
  );
}

function MemberRow({
  member,
  isMe,
  canManage,
  onChangeRole,
  onRemove,
}: {
  member: Member;
  isMe: boolean;
  canManage: boolean;
  onChangeRole: (role: 'OWNER' | 'MEMBER') => void;
  onRemove: () => void;
}) {
  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-medium text-brand-700 dark:bg-brand-700/25 dark:text-brand-200">
        {member.name.slice(0, 1)}
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">
          {member.name}
          {isMe && <span className="ml-1 text-xs text-slate-400">(나)</span>}
        </p>
        {/* 소셜 가입자는 이메일이 없을 수 있다 */}
        <p className="truncate text-xs text-slate-500">{member.email ?? '이메일 없음'}</p>
      </div>
      <span className="ml-auto shrink-0 text-xs text-slate-500">
        {member.role === 'OWNER' ? '관리자' : '멤버'}
      </span>
      {canManage && (
        <div className="flex shrink-0 gap-1">
          <Button size="sm" variant="ghost" onClick={() => onChangeRole(member.role === 'OWNER' ? 'MEMBER' : 'OWNER')}>
            {member.role === 'OWNER' ? '멤버로' : '관리자로'}
          </Button>
          <Button size="sm" variant="danger" onClick={onRemove}>
            제외
          </Button>
        </div>
      )}
    </li>
  );
}

function CreateProjectModal({
  open,
  onClose,
  workspaceId,
}: {
  open: boolean;
  onClose: () => void;
  workspaceId: number;
}) {
  const create = useCreateProject(workspaceId);
  const router = useRouter();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<ProjectInput>();

  return (
    <Modal open={open} onClose={onClose} title="새 프로젝트">
      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit((values) =>
          create.mutate(values, {
            onSuccess: (project) => {
              reset();
              onClose();
              router.push(`/projects/${project.id}`);
            },
          }),
        )}
      >
        <FormField
          label="이름"
          placeholder="예: 웹 리뉴얼"
          error={errors.name?.message}
          {...register('name', { required: '이름을 입력해주세요.', maxLength: { value: 50, message: '50자 이하' } })}
        />
        <FormField label="설명 (선택)" placeholder="무엇을 만드나요?" {...register('description')} />
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

function WorkspaceSettingsModal({
  open,
  onClose,
  workspace,
}: {
  open: boolean;
  onClose: () => void;
  workspace: Workspace;
}) {
  const update = useUpdateWorkspace(workspace.id);
  const toast = useToast();
  const { register, handleSubmit, formState: { errors } } = useForm<{ name: string; description?: string }>({
    values: { name: workspace.name, description: workspace.description ?? '' },
  });

  return (
    <Modal open={open} onClose={onClose} title="워크스페이스 설정">
      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit((values) =>
          update.mutate(values, {
            onSuccess: () => {
              toast('워크스페이스를 수정했습니다.');
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
