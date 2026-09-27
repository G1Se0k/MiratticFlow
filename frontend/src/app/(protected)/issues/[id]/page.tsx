'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { FormError, FormField, TextareaField } from '@/components/ui/FormField';
import { Icon } from '@/components/ui/Icon';
import { Menu } from '@/components/ui/Menu';
import { Modal, ModalActions } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { ChatPanel } from '@/components/chat/ChatPanel';
import { ProjectChatDock } from '@/components/chat/ProjectChatDock';
import { useIssue, useIssueMutations } from '@/hooks/useIssues';
import { useProject, useProjectMembers } from '@/hooks/useProjects';
import {
  ISSUE_PRIORITY_LABEL,
  ISSUE_STATUS_LABEL,
  type Issue,
  type IssueInput,
  type IssuePriority,
  type IssueStatus,
} from '@/lib/api/issue';
import type { Topic } from '@/lib/api/chat';
import type { ProjectMember } from '@/lib/api/project';
import { normalize } from '../../projects/[id]/IssueSection';
import { CommentSection } from './CommentSection';
import { TopicSection } from './TopicSection';

const STATUSES = Object.keys(ISSUE_STATUS_LABEL) as IssueStatus[];
const PRIORITIES = Object.keys(ISSUE_PRIORITY_LABEL) as IssuePriority[];

export default function IssueDetailPage() {
  const issueId = Number(useParams<{ id: string }>().id);
  const router = useRouter();
  const toast = useToast();

  const { data: issue, isPending, isError, error } = useIssue(issueId);
  const { data: project } = useProject(issue?.projectId ?? 0);
  const { data: members } = useProjectMembers(issue?.projectId ?? 0);
  const { changeStatus, remove } = useIssueMutations(issueId, issue?.projectId ?? 0);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  // 주제를 눌러도 페이지를 떠나지 않는다. 오른쪽 창만 열린다.
  const [openTopic, setOpenTopic] = useState<Topic | null>(null);

  if (isPending) {
    return (
      <div className="flex flex-col gap-6" role="status" aria-label="불러오는 중">
        <div className="flex flex-col gap-2">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-5 w-64" />
        </div>
        <Skeleton className="h-16" />
        <Skeleton className="h-24" />
      </div>
    );
  }
  if (isError || !issue) {
    return (
      <EmptyState
        icon="alert"
        title={error?.message ?? '이슈를 불러오지 못했습니다.'}
        action={
          <Button variant="secondary" size="sm" onClick={() => router.push('/workspaces')}>
            워크스페이스 목록으로
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-6 lg:flex-row">
      <div className="flex min-w-0 flex-1 flex-col gap-7">
        <header className="flex flex-col gap-1.5">
          <Link
            href={`/projects/${issue.projectId}`}
            className="flex w-fit items-center gap-1 text-xs text-ink-faint transition-colors hover:text-ink"
          >
            <Icon name="arrowLeft" className="size-3.5" />
            {project?.name ?? '프로젝트'}
          </Link>
          <div className="flex items-start gap-2">
            <h1 className="min-w-0 flex-1 text-[17px] font-semibold">
              <span className="mr-2 font-mono text-[13px] font-normal text-ink-faint">#{issue.number}</span>
              {issue.title}
            </h1>
            <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
              수정
            </Button>
            {issue.canDelete && (
              <Menu items={[{ label: '이슈 삭제', icon: 'trash', onSelect: () => setConfirmDelete(true), danger: true }]} />
            )}
          </div>
        </header>

        {/* 상태는 가장 자주 바뀌므로 상세에서도 바로 바꿀 수 있게 맨 앞에 둔다 */}
        <dl className="grid grid-cols-2 gap-y-3 rounded-card border border-line bg-surface px-4 py-3 sm:flex sm:items-center sm:gap-6">
          <div className="col-span-2 flex items-center gap-2 sm:col-span-1">
            <dt className="text-xs text-ink-soft">상태</dt>
            <dd>
              <Select
                aria-label="상태"
                value={issue.status}
                disabled={changeStatus.isPending}
                className="w-24"
                onChange={(e) =>
                  changeStatus.mutate(e.target.value as IssueStatus, {
                    onError: (error) => toast(error.message, 'error'),
                  })
                }
              >
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {ISSUE_STATUS_LABEL[status]}
                  </option>
                ))}
              </Select>
            </dd>
          </div>
          <Field label="우선순위">{ISSUE_PRIORITY_LABEL[issue.priority]}</Field>
          <Field label="담당자">
            {issue.assigneeName ? (
              <span className="flex items-center gap-1.5">
                <Avatar name={issue.assigneeName} size="sm" />
                {issue.assigneeName}
              </span>
            ) : (
              <span className="text-ink-faint">없음</span>
            )}
          </Field>
          <Field label="작성자">{issue.reporterName}</Field>
          <Field label="마감일">{issue.dueDate ?? <span className="text-ink-faint">없음</span>}</Field>
        </dl>

        <section className="flex flex-col gap-2">
          <h2 className="text-[13px] font-semibold">설명</h2>
          <p className="whitespace-pre-wrap text-[13px] leading-relaxed text-ink-soft">
            {issue.description || <span className="text-ink-faint">설명이 없습니다.</span>}
          </p>
        </section>

        <TopicSection issueId={issue.id} openTopicId={openTopic?.id ?? null} onOpen={setOpenTopic} />

        <CommentSection issueId={issue.id} />

        <EditIssueModal
          open={editing}
          onClose={() => setEditing(false)}
          issue={issue}
          members={members ?? []}
        />

        <Modal
          open={confirmDelete}
          onClose={() => setConfirmDelete(false)}
          title="이슈를 삭제할까요?"
          description="삭제하면 되돌릴 수 없습니다."
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
                    toast('이슈를 삭제했습니다.');
                    router.push(`/projects/${issue.projectId}`);
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
      </div>

      {openTopic && (
        <ChatPanel
          key={openTopic.id}
          topicId={openTopic.id}
          title={`# ${openTopic.name}`}
          subtitle={openTopic.description}
          onClose={() => setOpenTopic(null)}
          className="h-[30rem] w-full shrink-0 lg:sticky lg:top-6 lg:h-[calc(100dvh-6rem)] lg:w-80"
        />
      )}

      <ProjectChatDock projectId={issue.projectId} />
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd className="mt-0.5 truncate text-[13px]">{children}</dd>
    </div>
  );
}

function EditIssueModal({
  open,
  onClose,
  issue,
  members,
}: {
  open: boolean;
  onClose: () => void;
  issue: Issue;
  members: ProjectMember[];
}) {
  const { update } = useIssueMutations(issue.id, issue.projectId);
  const toast = useToast();
  const { register, handleSubmit, formState: { errors } } = useForm<IssueInput>({
    values: {
      title: issue.title,
      description: issue.description ?? '',
      priority: issue.priority,
      assigneeId: issue.assigneeId ?? undefined,
      dueDate: issue.dueDate ?? '',
    },
  });

  return (
    <Modal open={open} onClose={onClose} title="이슈 수정">
      <form
        className="flex flex-col gap-3.5"
        onSubmit={handleSubmit((values) =>
          update.mutate(normalize(values), {
            onSuccess: () => {
              toast('이슈를 수정했습니다.');
              onClose();
            },
          }),
        )}
      >
        <FormField
          label="제목"
          error={errors.title?.message}
          {...register('title', { required: '제목을 입력해주세요.', maxLength: { value: 100, message: '100자 이하' } })}
        />
        <TextareaField label="설명" rows={5} {...register('description')} />
        <div className="grid grid-cols-2 gap-2.5">
          <Select label="우선순위" {...register('priority')}>
            {PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {ISSUE_PRIORITY_LABEL[priority]}
              </option>
            ))}
          </Select>
          <Select label="담당자" {...register('assigneeId')}>
            <option value="">담당자 없음</option>
            {members.map((member) => (
              <option key={member.userId} value={member.userId}>
                {member.name}
              </option>
            ))}
          </Select>
        </div>
        <FormField label="마감일" type="date" {...register('dueDate')} />
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
