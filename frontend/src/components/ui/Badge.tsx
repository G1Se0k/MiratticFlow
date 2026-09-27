import type { ReactNode } from 'react';
import {
  ISSUE_PRIORITY_LABEL,
  ISSUE_STATUS_LABEL,
  type IssuePriority,
  type IssueStatus,
} from '@/lib/api/issue';

type Tone = 'neutral' | 'accent' | 'warn' | 'danger' | 'done';

const TONE: Record<Tone, string> = {
  neutral: 'bg-raised text-ink-soft',
  accent: 'bg-accent-soft text-accent-ink',
  warn: 'bg-warn-soft text-warn',
  danger: 'bg-danger-soft text-danger',
  done: 'bg-done-soft text-done',
};

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded px-1.5 py-0.5 text-[11px] font-medium ${TONE[tone]}`}
    >
      {children}
    </span>
  );
}

const STATUS_TONE: Record<IssueStatus, Tone> = {
  TODO: 'neutral',
  IN_PROGRESS: 'accent',
  REVIEW: 'warn',
  DONE: 'done',
};

/** 상태는 이슈에서 가장 먼저 읽는 값이라 점 하나를 앞에 붙여 눈에 걸리게 한다. */
export function StatusBadge({ status }: { status: IssueStatus }) {
  return (
    <Badge tone={STATUS_TONE[status]}>
      <span aria-hidden className="mr-1 size-1.5 rounded-full bg-current" />
      {ISSUE_STATUS_LABEL[status]}
    </Badge>
  );
}

const PRIORITY_TONE: Record<IssuePriority, Tone> = {
  LOW: 'neutral',
  MEDIUM: 'neutral',
  HIGH: 'warn',
  URGENT: 'danger',
};

/** 낮음·보통은 눈에 띌 필요가 없어 배지를 그리지 않는다. */
export const PriorityBadge = ({ priority }: { priority: IssuePriority }) =>
  priority === 'HIGH' || priority === 'URGENT' ? (
    <Badge tone={PRIORITY_TONE[priority]}>{ISSUE_PRIORITY_LABEL[priority]}</Badge>
  ) : null;
