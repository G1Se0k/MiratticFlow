'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
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
import type { ProjectMember } from '@/lib/api/project';
import { normalize } from '../../projects/[id]/IssueSection';

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

  if (isPending) return <Spinner />;
  if (isError || !issue) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-sm text-slate-500">{error?.message ?? '이슈를 불러오지 못했습니다.'}</p>
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
          href={`/projects/${issue.projectId}`}
          className="w-fit text-xs text-slate-500 hover:text-brand-600 dark:hover:text-brand-300"
        >
          ← {project?.name ?? '프로젝트'}
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm text-slate-400">ISSUE-{issue.number}</span>
          <h1 className="text-xl font-semibold tracking-tight">{issue.title}</h1>
          <div className="ml-auto flex gap-2">
            <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
              수정
            </Button>
            {issue.canDelete && (
              <Button size="sm" variant="danger" onClick={() => setConfirmDelete(true)}>
                삭제
              </Button>
            )}
          </div>
        </div>
      </header>

      <section className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
        {/* 상태는 가장 자주 바뀌므로 상세에서도 바로 바꿀 수 있게 둔다 */}
        <Select
          aria-label="상태"
          value={issue.status}
          disabled={changeStatus.isPending}
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
        <Field label="우선순위">{ISSUE_PRIORITY_LABEL[issue.priority]}</Field>
        <Field label="담당자">{issue.assigneeName ?? '없음'}</Field>
        <Field label="작성자">{issue.reporterName}</Field>
        <Field label="마감일">{issue.dueDate ?? '없음'}</Field>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-slate-500">설명</h2>
        <p className="whitespace-pre-wrap text-sm leading-relaxed">
          {issue.description || <span className="text-slate-400">설명이 없습니다.</span>}
        </p>
      </section>

      <EditIssueModal
        open={editing}
        onClose={() => setEditing(false)}
        issue={issue}
        members={members ?? []}
      />

      <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} title="이슈를 삭제할까요?">
        <p className="text-sm text-slate-500">삭제하면 되돌릴 수 없습니다.</p>
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
        </div>
      </Modal>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs text-slate-400">{label}</p>
      <p className="text-sm">{children}</p>
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
        className="flex flex-col gap-4"
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
        <div className="flex flex-col gap-1.5">
          <label htmlFor="edit-description" className="text-sm font-medium text-slate-700 dark:text-slate-300">
            설명
          </label>
          <textarea
            id="edit-description"
            rows={5}
            {...register('description')}
            className="rounded-md border border-slate-300 bg-white p-3 text-sm outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-slate-400"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
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
