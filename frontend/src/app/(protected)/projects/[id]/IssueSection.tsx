'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { PriorityBadge } from '@/components/ui/Badge';
import { FormError, FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { Select } from '@/components/ui/Select';
import { SkeletonList } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { useCreateIssue, useIssues, useQuickStatusChange } from '@/hooks/useIssues';
import {
  ISSUE_PRIORITY_LABEL,
  ISSUE_STATUS_LABEL,
  type IssueFilter,
  type IssueInput,
  type IssuePriority,
  type IssueStatus,
} from '@/lib/api/issue';
import type { ProjectMember } from '@/lib/api/project';

const STATUSES = Object.keys(ISSUE_STATUS_LABEL) as IssueStatus[];
const PRIORITIES = Object.keys(ISSUE_PRIORITY_LABEL) as IssuePriority[];

const SORTS = [
  { value: 'id,desc', label: '최신순' },
  { value: 'id,asc', label: '오래된순' },
  { value: 'dueDate,asc', label: '마감 임박순' },
  { value: 'priority,desc', label: '우선순위순' },
];

export function IssueSection({ projectId, members }: { projectId: number; members: ProjectMember[] }) {
  const [filter, setFilter] = useState<IssueFilter>({ sort: 'id,desc' });
  const { data, isPending } = useIssues(projectId, filter);
  const quickStatus = useQuickStatusChange(projectId);
  const toast = useToast();
  const [creating, setCreating] = useState(false);

  // 필터를 바꾸면 보고 있던 페이지 번호는 의미가 없어지므로 1페이지로 되돌린다.
  const change = (patch: Partial<IssueFilter>) => setFilter((prev) => ({ ...prev, ...patch, page: 0 }));

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-slate-500">이슈 {data?.totalElements ?? 0}개</h2>
        <Button size="sm" onClick={() => setCreating(true)}>
          새 이슈
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <input
          type="search"
          placeholder="제목 검색"
          aria-label="이슈 제목 검색"
          value={filter.keyword ?? ''}
          onChange={(e) => change({ keyword: e.target.value })}
          className="h-9 min-w-40 flex-1 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-slate-400"
        />
        <Select
          aria-label="상태 필터"
          value={filter.status ?? ''}
          onChange={(e) => change({ status: e.target.value as IssueStatus | '' })}
        >
          <option value="">전체 상태</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {ISSUE_STATUS_LABEL[status]}
            </option>
          ))}
        </Select>
        <Select
          aria-label="우선순위 필터"
          value={filter.priority ?? ''}
          onChange={(e) => change({ priority: e.target.value as IssuePriority | '' })}
        >
          <option value="">전체 우선순위</option>
          {PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {ISSUE_PRIORITY_LABEL[priority]}
            </option>
          ))}
        </Select>
        <Select
          aria-label="담당자 필터"
          value={filter.assigneeId ?? ''}
          onChange={(e) => change({ assigneeId: e.target.value ? Number(e.target.value) : '' })}
        >
          <option value="">전체 담당자</option>
          {members.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.name}
            </option>
          ))}
        </Select>
        <Select aria-label="정렬" value={filter.sort} onChange={(e) => change({ sort: e.target.value })}>
          {SORTS.map((sort) => (
            <option key={sort.value} value={sort.value}>
              {sort.label}
            </option>
          ))}
        </Select>
      </div>

      {isPending ? (
        <SkeletonList rows={4} className="h-14" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          title="이슈가 없습니다"
          description={filter.keyword || filter.status || filter.priority || filter.assigneeId
            ? '조건에 맞는 이슈가 없습니다.'
            : '첫 이슈를 등록해 할 일을 나눠보세요.'}
        />
      ) : (
        <ul className="divide-y divide-slate-200 rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {data?.content.map((issue) => (
            <li key={issue.id} className="flex flex-wrap items-center gap-2 px-4 py-3">
              <span className="shrink-0 font-mono text-xs text-slate-400">ISSUE-{issue.number}</span>
              <Link href={`/issues/${issue.id}`} className="min-w-0 flex-1 truncate text-sm font-medium hover:underline">
                {issue.title}
              </Link>
              <PriorityBadge priority={issue.priority} />
              {issue.dueDate && <span className="shrink-0 text-xs text-slate-400">~{issue.dueDate}</span>}
              <span className="shrink-0 text-xs text-slate-500">{issue.assigneeName ?? '담당자 없음'}</span>
              {/* 목록에서 바로 상태를 바꾼다. 상세로 들어갔다 나오는 왕복을 줄인다. */}
              <Select
                aria-label={`${issue.title} 상태`}
                value={issue.status}
                disabled={quickStatus.isPending}
                onChange={(e) =>
                  quickStatus.mutate(
                    { issueId: issue.id, status: e.target.value as IssueStatus },
                    { onError: (error) => toast(error.message, 'error') },
                  )
                }
              >
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {ISSUE_STATUS_LABEL[status]}
                  </option>
                ))}
              </Select>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <Button
            size="sm"
            variant="ghost"
            disabled={data.page === 0}
            onClick={() => setFilter((prev) => ({ ...prev, page: data.page - 1 }))}
          >
            이전
          </Button>
          <span className="text-slate-500">
            {data.page + 1} / {data.totalPages}
          </span>
          <Button
            size="sm"
            variant="ghost"
            disabled={data.page + 1 >= data.totalPages}
            onClick={() => setFilter((prev) => ({ ...prev, page: data.page + 1 }))}
          >
            다음
          </Button>
        </div>
      )}

      <CreateIssueModal
        open={creating}
        onClose={() => setCreating(false)}
        projectId={projectId}
        members={members}
      />
    </section>
  );
}

function CreateIssueModal({
  open,
  onClose,
  projectId,
  members,
}: {
  open: boolean;
  onClose: () => void;
  projectId: number;
  members: ProjectMember[];
}) {
  const create = useCreateIssue(projectId);
  const toast = useToast();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<IssueInput>({
    defaultValues: { priority: 'MEDIUM' },
  });

  return (
    <Modal open={open} onClose={onClose} title="새 이슈">
      <form
        className="flex flex-col gap-4"
        onSubmit={handleSubmit((values) =>
          create.mutate(normalize(values), {
            onSuccess: () => {
              toast('이슈를 등록했습니다.');
              reset(); // 인자를 주면 빠뜨린 필드가 이전 값 그대로 남는다 — defaultValues 로 되돌린다
              onClose();
            },
          }),
        )}
      >
        <FormField
          label="제목"
          placeholder="무엇을 해야 하나요?"
          error={errors.title?.message}
          {...register('title', { required: '제목을 입력해주세요.', maxLength: { value: 100, message: '100자 이하' } })}
        />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="issue-description" className="text-sm font-medium text-slate-700 dark:text-slate-300">
            설명 (선택)
          </label>
          <textarea
            id="issue-description"
            rows={4}
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
        <FormField label="마감일 (선택)" type="date" {...register('dueDate')} />
        {create.isError && <FormError>{create.error.message}</FormError>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={create.isPending}>
            등록
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** select 와 date input 은 비어 있을 때 빈 문자열을 준다. 서버에는 null 로 보내야 한다. */
export function normalize(values: IssueInput): IssueInput {
  return {
    ...values,
    assigneeId: values.assigneeId ? Number(values.assigneeId) : null,
    dueDate: values.dueDate || null,
    description: values.description || undefined,
  };
}
