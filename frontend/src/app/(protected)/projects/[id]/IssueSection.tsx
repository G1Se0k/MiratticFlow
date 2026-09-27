'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { PriorityBadge } from '@/components/ui/Badge';
import { FormError, FormField, TextareaField, controlClass } from '@/components/ui/FormField';
import { Icon } from '@/components/ui/Icon';
import { Modal, ModalActions } from '@/components/ui/Modal';
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

/** 목록 안의 상태 select 는 테두리를 숨겨 행과 하나로 보이게 한다. */
const inlineSelect = 'border-transparent bg-transparent hover:border-line';

export function IssueSection({ projectId, members }: { projectId: number; members: ProjectMember[] }) {
  const [filter, setFilter] = useState<IssueFilter>({ sort: 'id,desc' });
  const { data, isPending } = useIssues(projectId, filter);
  const quickStatus = useQuickStatusChange(projectId);
  const toast = useToast();
  const [creating, setCreating] = useState(false);

  // 필터를 바꾸면 보고 있던 페이지 번호는 의미가 없어지므로 1페이지로 되돌린다.
  const change = (patch: Partial<IssueFilter>) => setFilter((prev) => ({ ...prev, ...patch, page: 0 }));
  const filtered = Boolean(filter.keyword || filter.status || filter.priority || filter.assigneeId);

  return (
    <section className="flex flex-col gap-2.5">
      <div className="flex items-center justify-between">
        <h2 className="text-[13px] font-semibold">이슈 {data?.totalElements ?? 0}</h2>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Icon name="plus" className="size-3.5" />
          새 이슈
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <div className="relative min-w-40 flex-1">
          <Icon name="search" className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-ink-faint" />
          <input
            type="search"
            placeholder="제목 검색"
            aria-label="이슈 제목 검색"
            value={filter.keyword ?? ''}
            onChange={(e) => change({ keyword: e.target.value })}
            className={`h-8 pl-8 pr-2.5 ${controlClass}`}
          />
        </div>
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
        <SkeletonList rows={4} className="h-11" />
      ) : data && data.content.length === 0 ? (
        <div className="rounded-card border border-line bg-surface">
          <EmptyState
            icon="check"
            title={filtered ? '조건에 맞는 이슈가 없습니다' : '아직 이슈가 없습니다'}
            description={filtered ? '검색어나 필터를 바꿔보세요.' : '첫 이슈를 등록해 할 일을 나눠보세요.'}
          />
        </div>
      ) : (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {data?.content.map((issue) => (
            <li key={issue.id} className="flex flex-wrap items-center gap-2 px-3 py-2 transition-colors hover:bg-raised">
              <span className="w-9 shrink-0 font-mono text-[11px] text-ink-faint">#{issue.number}</span>
              <Link
                href={`/issues/${issue.id}`}
                className="min-w-0 flex-1 truncate text-[13px] font-medium hover:text-accent"
              >
                {issue.title}
              </Link>
              <PriorityBadge priority={issue.priority} />
              {issue.dueDate && (
                <span className="flex shrink-0 items-center gap-1 text-[11px] text-ink-faint">
                  <Icon name="calendar" className="size-3" />
                  {issue.dueDate.slice(5)}
                </span>
              )}
              {issue.assigneeName ? (
                <span className="flex shrink-0 items-center gap-1.5 text-xs text-ink-soft">
                  <Avatar name={issue.assigneeName} size="sm" />
                  <span className="hidden sm:inline">{issue.assigneeName}</span>
                </span>
              ) : (
                <span className="shrink-0 text-xs text-ink-faint">담당자 없음</span>
              )}
              {/* 목록에서 바로 상태를 바꾼다. 상세로 들어갔다 나오는 왕복을 줄인다. */}
              <Select
                aria-label={`${issue.title} 상태`}
                value={issue.status}
                disabled={quickStatus.isPending}
                className={`${inlineSelect} w-24`}
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
        <div className="flex items-center justify-center gap-2">
          <Button
            size="sm"
            variant="ghost"
            disabled={data.page === 0}
            onClick={() => setFilter((prev) => ({ ...prev, page: data.page - 1 }))}
          >
            이전
          </Button>
          <span className="text-xs tabular-nums text-ink-soft">
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
        className="flex flex-col gap-3.5"
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
        <TextareaField label="설명 (선택)" rows={4} {...register('description')} />
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
        <FormField label="마감일 (선택)" type="date" {...register('dueDate')} />
        {create.isError && <FormError>{create.error.message}</FormError>}
        <ModalActions>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={create.isPending}>
            등록
          </Button>
        </ModalActions>
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
