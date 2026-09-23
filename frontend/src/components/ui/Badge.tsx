import type { ReactNode } from 'react';
import { ISSUE_PRIORITY_LABEL, type IssuePriority } from '@/lib/api/issue';

type Tone = 'slate' | 'amber' | 'red';

const TONE: Record<Tone, string> = {
  slate: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
  amber: 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
  red: 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300',
};

function Badge({ tone = 'slate', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex shrink-0 rounded px-1.5 py-0.5 text-xs font-medium ${TONE[tone]}`}>{children}</span>
  );
}

const PRIORITY_TONE: Record<IssuePriority, Tone> = {
  LOW: 'slate',
  MEDIUM: 'slate',
  HIGH: 'amber',
  URGENT: 'red',
};

/** 낮음·보통은 눈에 띌 필요가 없어 배지를 그리지 않는다. */
export const PriorityBadge = ({ priority }: { priority: IssuePriority }) =>
  priority === 'HIGH' || priority === 'URGENT' ? (
    <Badge tone={PRIORITY_TONE[priority]}>{ISSUE_PRIORITY_LABEL[priority]}</Badge>
  ) : null;
