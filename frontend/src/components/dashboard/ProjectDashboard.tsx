'use client';

import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { useProjectStats } from '@/hooks/useProjects';
import { ISSUE_PRIORITY_LABEL, ISSUE_STATUS_LABEL } from '@/lib/api/issue';

/**
 * 차트 색은 라이트·다크 어디서도 읽히는 중간 명도로 고정한다 (recharts 는 CSS 변수를 못 받는다).
 * 순서와 의미는 이슈 목록의 배지와 같다 — 화면마다 색이 다르면 읽는 사람이 다시 배워야 한다.
 */
const STATUS_COLOR = ['#9b9ba4', '#3b7fe8', '#d9a13b', '#34a853']; // TODO, IN_PROGRESS, REVIEW, DONE
const PRIORITY_COLOR = ['#9b9ba4', '#3b7fe8', '#d9a13b', '#e05656']; // LOW, MEDIUM, HIGH, URGENT

/** 툴팁은 recharts 기본 흰 상자라 다크 모드에서 튄다. 토큰을 그대로 넘겨 맞춘다. */
const TOOLTIP_STYLE = {
  background: 'var(--c-surface)',
  border: '1px solid var(--c-line)',
  borderRadius: 8,
  fontSize: 12,
  padding: '4px 8px',
  color: 'var(--c-ink)',
} as const;

export function ProjectDashboard({ projectId }: { projectId: number }) {
  const { data: stats, isPending } = useProjectStats(projectId);

  if (isPending) return <DashboardSkeleton />;
  if (!stats) return null;

  const statusData = stats.byStatus.map((s) => ({ name: ISSUE_STATUS_LABEL[s.status], value: s.count }));
  const priorityData = stats.byPriority.map((p) => ({ name: ISSUE_PRIORITY_LABEL[p.priority], value: p.count }));

  return (
    <section className="flex flex-col gap-3">
      {/* 숫자 네 개는 카드 네 장이 아니라 한 줄이다. 테두리를 나누면 관계가 끊겨 보인다. */}
      <dl className="grid grid-cols-2 divide-line overflow-hidden rounded-card border border-line bg-surface sm:grid-cols-4 sm:divide-x">
        <Stat label="전체 이슈" value={stats.total} />
        <Stat label="진행 중" value={stats.inProgress} />
        <Stat label="완료" value={stats.done} />
        <Stat label="내 담당" value={stats.mine} />
      </dl>

      {stats.total === 0 ? (
        <div className="rounded-card border border-line bg-surface">
          <EmptyState
            title="아직 이슈가 없습니다"
            description="이슈를 등록하면 상태와 우선순위 분포가 여기에 나타납니다."
          />
        </div>
      ) : (
        <div className="grid gap-3 lg:grid-cols-3">
          <Panel title="상태별">
            <ResponsiveContainer width="100%" height={160}>
              <PieChart>
                <Pie data={statusData} dataKey="value" nameKey="name" innerRadius={38} outerRadius={62} strokeWidth={0}>
                  {statusData.map((entry, index) => (
                    <Cell key={entry.name} fill={STATUS_COLOR[index]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={TOOLTIP_STYLE} />
              </PieChart>
            </ResponsiveContainer>
            <Legend items={statusData.map((d, i) => ({ ...d, color: STATUS_COLOR[i] }))} />
          </Panel>

          <Panel title="우선순위별">
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={priorityData} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
                <XAxis
                  dataKey="name"
                  tickLine={false}
                  axisLine={false}
                  fontSize={11}
                  stroke="var(--c-ink-faint)"
                  dy={2}
                />
                <Tooltip cursor={{ fill: 'transparent' }} contentStyle={TOOLTIP_STYLE} />
                <Bar dataKey="value" radius={[3, 3, 0, 0]} maxBarSize={36}>
                  {priorityData.map((entry, index) => (
                    <Cell key={entry.name} fill={PRIORITY_COLOR[index]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Panel>

          {/* 담당자별은 이름이 있어야 읽히는 데이터라 차트보다 목록이 낫다. */}
          <Panel title="담당자별">
            <ul className="flex flex-col gap-2">
              {stats.byAssignee.map((assignee) => (
                <li key={assignee.userId ?? 'none'} className="flex flex-col gap-1">
                  <div className="flex justify-between text-xs">
                    <span className={assignee.userId ? 'text-ink-soft' : 'text-ink-faint'}>
                      {assignee.name ?? '담당자 없음'}
                    </span>
                    <span className="tabular-nums text-ink-faint">{assignee.count}</span>
                  </div>
                  <div className="h-1 rounded-full bg-raised">
                    <div
                      className="h-full rounded-full bg-accent"
                      style={{ width: `${(assignee.count / stats.total) * 100}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          </Panel>
        </div>
      )}

      <Panel title="최근 활동">
        {stats.recentActivity.length === 0 ? (
          <p className="text-[13px] text-ink-faint">아직 활동이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {stats.recentActivity.map((activity) => (
              <li key={activity.createdAt + activity.content} className="flex gap-2.5 text-[13px]">
                <span className="shrink-0 font-mono text-[11px] leading-5 text-ink-faint">
                  {activity.createdAt.slice(5, 16).replace('T', ' ')}
                </span>
                <span className="min-w-0 truncate text-ink-soft">{activity.content}</span>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </section>
  );
}

/** 숫자 줄 + 패널 3개. 실제 배치와 같은 모양이라 데이터가 와도 화면이 흔들리지 않는다. */
function DashboardSkeleton() {
  return (
    <section className="flex flex-col gap-3" role="status" aria-label="불러오는 중">
      <Skeleton className="h-[70px]" />
      <div className="grid gap-3 lg:grid-cols-3">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-[230px]" />
        ))}
      </div>
    </section>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="border-b border-line px-4 py-3 last:border-b-0 sm:border-b-0">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd className="mt-0.5 text-xl font-semibold tabular-nums tracking-[-0.02em]">{value}</dd>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-card border border-line bg-surface p-4">
      {/* 아래 이슈·참여자 섹션과 같은 단계다. h3 으로 두면 h1 다음에 h3 이 와서 단계를 건너뛴다. */}
      <h2 className="mb-2.5 text-xs font-semibold text-ink-soft">{title}</h2>
      {children}
    </div>
  );
}

function Legend({ items }: { items: { name: string; value: number; color: string }[] }) {
  return (
    <ul className="mt-1 flex flex-wrap justify-center gap-x-3 gap-y-1">
      {items.map((item) => (
        <li key={item.name} className="flex items-center gap-1.5 text-[11px] text-ink-soft">
          <span aria-hidden className="size-1.5 rounded-full" style={{ backgroundColor: item.color }} />
          {item.name} <span className="tabular-nums text-ink-faint">{item.value}</span>
        </li>
      ))}
    </ul>
  );
}
