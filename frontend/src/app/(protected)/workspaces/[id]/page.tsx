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
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { useMe } from '@/hooks/useAuth';
import { useCreateProject, useProjects } from '@/hooks/useProjects';
import {
  useInviteLinks,
  useUpdateWorkspace,
  useInviteMutations,
  useJoinCode,
  useDeleteWorkspace,
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
  const { changeRole, removeMember } = useMemberMutations(workspaceId);
  const { data: projects } = useProjects(workspaceId);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [creatingProject, setCreatingProject] = useState(false);
  const [editingWorkspace, setEditingWorkspace] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isPending) return <WorkspaceSkeleton />;
  if (isError || !workspace) {
    return (
      <EmptyState
        icon="alert"
        title={error?.message ?? '워크스페이스를 불러오지 못했습니다.'}
        action={
          <Button variant="secondary" size="sm" onClick={() => router.push('/workspaces')}>
            목록으로
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-7">
      <header className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <h1 className="text-[17px] font-semibold">{workspace.name}</h1>
          <p className="mt-0.5 text-[13px] text-ink-soft">
            {workspace.description || '설명 없음'} · 멤버 {members?.length ?? 0}명
          </p>
        </div>
        {isOwner && (
          <Button size="sm" variant="secondary" onClick={() => setEditingWorkspace(true)}>
            <Icon name="settings" className="size-3.5" />
            설정
          </Button>
        )}
        <Menu
          items={[
            { label: '워크스페이스 나가기', icon: 'logout', onSelect: () => setConfirmLeave(true), danger: true },
            ...(isOwner
              ? [{ label: '워크스페이스 삭제', icon: 'trash' as const, onSelect: () => setConfirmDelete(true), danger: true }]
              : []),
          ]}
        />
      </header>

      {/* 프로젝트 — 워크스페이스에서 가장 자주 쓰는 화면이라 맨 위에 둔다 */}
      <section className="flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <h2 className="text-[13px] font-semibold">프로젝트 {projects?.length ?? 0}</h2>
          <Button size="sm" variant="secondary" onClick={() => setCreatingProject(true)}>
            <Icon name="plus" className="size-3.5" />
            새 프로젝트
          </Button>
        </div>

        {projects?.length === 0 ? (
          <div className="rounded-card border border-line bg-surface">
            <EmptyState
              icon="folder"
              title="아직 프로젝트가 없습니다"
              description="프로젝트를 만들면 그 안에서 이슈를 관리하고 팀과 이야기할 수 있습니다."
              action={
                <Button size="sm" onClick={() => setCreatingProject(true)}>
                  새 프로젝트 만들기
                </Button>
              }
            />
          </div>
        ) : (
          <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {projects?.map((project) => (
              <li key={project.id}>
                <Link
                  href={`/projects/${project.id}`}
                  className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-raised"
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-raised text-ink-soft">
                    <Icon name="folder" className="size-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-1.5">
                      <span className="truncate text-[13px] font-medium">{project.name}</span>
                      {project.status === 'ARCHIVED' && <Badge>보관됨</Badge>}
                    </span>
                    <span className="mt-0.5 block truncate text-[13px] text-ink-soft">
                      {project.description || '설명 없음'}
                    </span>
                  </span>
                  <span className="hidden shrink-0 text-xs text-ink-faint sm:block">
                    참여자 {project.memberCount}명
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
      </section>

      <section className="flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <h2 className="text-[13px] font-semibold">멤버 {members?.length ?? 0}</h2>
          {isOwner && (
            <Button size="sm" variant="secondary" onClick={() => setInviting(true)}>
              <Icon name="link" className="size-3.5" />
              멤버 초대
            </Button>
          )}
        </div>
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
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

      {isOwner && <InviteModal open={inviting} onClose={() => setInviting(false)} workspaceId={workspaceId} />}

      <Modal
        open={confirmLeave}
        onClose={() => setConfirmLeave(false)}
        title="워크스페이스를 나갈까요?"
        description="나가면 이 워크스페이스의 내용을 볼 수 없습니다."
      >
        <ModalActions>
          <Button variant="ghost" size="sm" onClick={() => setConfirmLeave(false)}>
            취소
          </Button>
          <Button
            variant="dangerSolid"
            size="sm"
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
        </ModalActions>
      </Modal>

      <DeleteWorkspaceModal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        workspace={workspace}
      />
    </div>
  );
}

function WorkspaceSkeleton() {
  return (
    <div className="flex flex-col gap-7" role="status" aria-label="불러오는 중">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-3.5 w-64" />
      </div>
      <Skeleton className="h-44" />
      <Skeleton className="h-32" />
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
    <li className="flex items-center gap-3 px-4 py-2.5">
      <Avatar name={member.name} />
      <div className="min-w-0">
        <p className="truncate text-[13px] font-medium">
          {member.name}
          {isMe && <span className="ml-1 text-xs font-normal text-ink-faint">(나)</span>}
        </p>
        {/* 소셜 가입자는 이메일이 없을 수 있다 */}
        <p className="truncate text-xs text-ink-soft">{member.email ?? '이메일 없음'}</p>
      </div>
      <div className="ml-auto flex shrink-0 items-center gap-1.5">
        {member.role === 'OWNER' ? <Badge tone="accent">관리자</Badge> : <Badge>멤버</Badge>}
        {canManage && (
          <Menu
            label={`${member.name} 관리`}
            items={[
              {
                label: member.role === 'OWNER' ? '멤버로 변경' : '관리자로 변경',
                icon: 'refresh',
                onSelect: () => onChangeRole(member.role === 'OWNER' ? 'MEMBER' : 'OWNER'),
              },
              { label: '워크스페이스에서 제외', icon: 'trash', onSelect: onRemove, danger: true },
            ]}
          />
        )}
      </div>
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
        className="flex flex-col gap-3.5"
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
        className="flex flex-col gap-3.5"
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

/**
 * 초대는 관리자만, 그리고 필요할 때만 쓴다.
 * 예전에는 참여 코드와 링크 목록이 화면 중간을 차지하고 있었다 — 모달로 옮겨 본문을 비웠다.
 */
function InviteModal({
  open,
  onClose,
  workspaceId,
}: {
  open: boolean;
  onClose: () => void;
  workspaceId: number;
}) {
  const toast = useToast();
  const { data: joinCode } = useJoinCode(workspaceId, open);
  const { data: links } = useInviteLinks(workspaceId, open);
  const { createLink, revokeLink, regenerateCode } = useInviteMutations(workspaceId);

  const copy = async (text: string, label: string) => {
    await navigator.clipboard.writeText(text);
    toast(`${label}를 복사했습니다.`);
  };

  const inviteUrl = (code: string) => `${window.location.origin}/invite/${code}`;

  return (
    <Modal open={open} onClose={onClose} title="멤버 초대">
      <div className="flex flex-col gap-5">
        {/* 상시 코드 — 아는 사람은 누구나 참여 */}
        <div className="flex flex-col gap-2">
          <p className="text-xs font-medium text-ink-soft">참여 코드</p>
          <div className="flex items-center gap-2 rounded-md border border-line bg-raised px-2.5 py-2">
            <span className="flex-1 font-mono text-[15px] tracking-widest">{joinCode?.code ?? '···'}</span>
            <Button size="sm" variant="ghost" onClick={() => joinCode && copy(joinCode.code, '코드')}>
              <Icon name="copy" className="size-3.5" />
              복사
            </Button>
          </div>
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs text-ink-faint">코드를 아는 사람은 누구나 참여할 수 있습니다.</p>
            <Button
              size="sm"
              variant="ghost"
              onClick={() =>
                regenerateCode.mutate(undefined, {
                  onSuccess: () => toast('새 코드를 발급했습니다. 이전 코드는 사용할 수 없습니다.'),
                })
              }
              disabled={regenerateCode.isPending}
            >
              재발급
            </Button>
          </div>
        </div>

        {/* 일회용 링크 — 특정인에게 전달 */}
        <div className="flex flex-col gap-2 border-t border-line pt-4">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-xs font-medium text-ink-soft">초대 링크</p>
              <p className="text-xs text-ink-faint">한 번만 사용할 수 있고 7일 뒤 만료됩니다.</p>
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
              <Icon name="plus" className="size-3.5" />
              링크 만들기
            </Button>
          </div>

          {links && links.length > 0 && (
            <ul className="flex flex-col gap-1.5">
              {links.map((link) => (
                <li key={link.id} className="flex items-center gap-1.5 rounded-md bg-raised px-2.5 py-1.5">
                  <span className="min-w-0 flex-1 truncate font-mono text-xs text-ink-soft">
                    {inviteUrl(link.code)}
                  </span>
                  <span className="shrink-0 text-xs text-ink-faint">
                    {link.usedCount}/{link.maxUses}
                  </span>
                  <Button size="icon" variant="ghost" aria-label="복사" onClick={() => copy(inviteUrl(link.code), '초대 링크')}>
                    <Icon name="copy" className="size-3.5" />
                  </Button>
                  <Button
                    size="icon"
                    variant="danger"
                    aria-label="링크 폐기"
                    onClick={() => revokeLink.mutate(link.id, { onSuccess: () => toast('링크를 폐기했습니다.') })}
                  >
                    <Icon name="trash" className="size-3.5" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Modal>
  );
}

/**
 * 워크스페이스 삭제. 프로젝트·이슈·댓글·채팅이 전부 함께 사라지므로
 * 프로젝트 삭제처럼 버튼 한 번으로 끝내지 않고 이름을 직접 입력받는다 (/account 탈퇴와 같은 방식).
 */
function DeleteWorkspaceModal({
  open,
  onClose,
  workspace,
}: {
  open: boolean;
  onClose: () => void;
  workspace: Workspace;
}) {
  const remove = useDeleteWorkspace(workspace.id);
  const router = useRouter();
  const toast = useToast();
  const [confirmName, setConfirmName] = useState('');

  const close = () => {
    setConfirmName('');
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={close}
      title="워크스페이스를 삭제할까요?"
      description="프로젝트와 이슈, 댓글, 채팅이 모두 삭제되고 되돌릴 수 없습니다. 멤버도 접근할 수 없게 됩니다."
    >
      <div className="flex flex-col gap-3.5">
        <FormField
          label="확인을 위해 워크스페이스 이름을 입력해주세요"
          placeholder={workspace.name}
          value={confirmName}
          onChange={(e) => setConfirmName(e.target.value)}
        />
        {remove.isError && <FormError>{remove.error.message}</FormError>}
        <ModalActions>
          <Button variant="ghost" size="sm" onClick={close}>
            취소
          </Button>
          <Button
            variant="dangerSolid"
            size="sm"
            disabled={confirmName !== workspace.name || remove.isPending}
            onClick={() =>
              remove.mutate(undefined, {
                onSuccess: () => {
                  toast('워크스페이스를 삭제했습니다.');
                  router.replace('/workspaces');
                },
              })
            }
          >
            삭제
          </Button>
        </ModalActions>
      </div>
    </Modal>
  );
}
