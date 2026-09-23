'use client';

import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts';
import { EmptyState } from '@/components/ui/EmptyState';
import { Spinner } from '@/components/ui/Spinner';
import { useProjectStats } from '@/hooks/useProjects';
import { ISSUE_PRIORITY_LABEL, ISSUE_STATUS_LABEL } from '@/lib/api/issue';

// 이슈 목록의 상태 배지와 같은 색 계열을 쓴다. 화면마다 색이 다르면 읽는 사람이 다시 배워야 한다.
const STATUS_COLOR = ['#94a3b8', '#6366f1', '#f59e0b', '#10b981']; // TODO, IN_PROGRESS, REVIEW, DONE
const PRIORITY_COLOR = ['#94a3b8', '#60a5fa', '#f59e0b', '#ef4444']; // LOW, MEDIUM, HIGH, URGENT

export function ProjectDashboard({ projectId }: { projectId: number }) {
  const { data: stats, isPending } = useProjectStats(projectId);

  if (isPending) return <Spinner />;
  if (!stats) return null;

  const statusData = stats.byStatus.map((s) => ({ name: ISSUE_STATUS_LABEL[s.status], value: s.count }));
  const priorityData = stats.byPriority.map((p) => ({ name: ISSUE_PRIORITY_LABEL[p.priority], value: p.count }));

  return (
    <section className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard label="전체 이슈" value={stats.total} />
        <StatCard label="진행 중" value={stats.inProgress} />
        <StatCard label="완료" value={stats.done} />
        <StatCard label="내 담당" value={stats.mine} />
      </div>

      {stats.total === 0 ? (
        <EmptyState
          title="아직 이슈가 없습니다"
          description="이슈를 등록하면 상태와 우선순위 분포가 여기에 나타납니다."
        />
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          <Panel title="상태별">
            <ResponsiveContainer width="100%" height={180}>
              <PieChart>
                <Pie data={statusData} dataKey="value" nameKey="name" innerRadius={42} outerRadius={70}>
                  {statusData.map((entry, index) => (
                    <Cell key={entry.name} fill={STATUS_COLOR[index]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
            <Legend items={statusData.map((d, i) => ({ ...d, color: STATUS_COLOR[i] }))} />
          </Panel>

          <Panel title="우선순위별">
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={priorityData}>
                <XAxis dataKey="name" tickLine={false} axisLine={false} fontSize={12} stroke="#94a3b8" />
                <Tooltip cursor={{ fill: 'transparent' }} />
                <Bar dataKey="value" radius={[4, 4, 0, 0]}>
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
                    <span className={assignee.userId ? '' : 'text-slate-400'}>
                      {assignee.name ?? '담당자 없음'}
                    </span>
                    <span className="text-slate-500">{assignee.count}건</span>
                  </div>
                  <div className="h-1.5 rounded-full bg-slate-100 dark:bg-slate-800">
                    <div
                      className="h-full rounded-full bg-brand-600"
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
          <p className="text-sm text-slate-400">아직 활동이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {stats.recentActivity.map((activity) => (
              <li key={activity.createdAt + activity.content} className="flex gap-3 text-sm">
                <span className="shrink-0 font-mono text-xs text-slate-400">
                  {activity.createdAt.slice(5, 16).replace('T', ' ')}
                </span>
                <span className="min-w-0 truncate text-slate-600 dark:text-slate-300">{activity.content}</span>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </section>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
      <h3 className="mb-3 text-xs font-medium text-slate-500">{title}</h3>
      {children}
    </div>
  );
}

function Legend({ items }: { items: { name: string; value: number; color: string }[] }) {
  return (
    <ul className="mt-2 flex flex-wrap justify-center gap-x-3 gap-y-1">
      {items.map((item) => (
        <li key={item.name} className="flex items-center gap-1.5 text-xs text-slate-500">
          <span aria-hidden className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
          {item.name} {item.value}
        </li>
      ))}
    </ul>
  );
}
