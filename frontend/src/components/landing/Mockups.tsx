import type { ReactNode } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { PriorityBadge, StatusBadge } from '@/components/ui/Badge';
import { Icon, type IconName } from '@/components/ui/Icon';
import type { IssuePriority, IssueStatus } from '@/lib/api/issue';

/**
 * 랜딩의 기능 소개 그림. 앱 화면(프로젝트 이슈 목록 · 대시보드 · 알림)을 고정 데이터로 그린다.
 * 배지 · 아바타 · 아이콘은 앱의 컴포넌트를 그대로 써서 앱과 같게 보인다. 화면이 바뀌면 같이 고친다.
 */
function Frame({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div
      role="img"
      aria-label={label}
      className="rounded-[12px] border border-line bg-canvas p-3.5 text-[13px] shadow-[0_1px_3px_rgb(0_0_0/0.04),0_24px_56px_-28px_rgb(0_23_51/0.2)]"
    >
      <div aria-hidden>{children}</div>
    </div>
  );
}

const ISSUES: { n: number; title: string; status: IssueStatus; priority: IssuePriority; who: string; due: string }[] = [
  { n: 12, title: '로그인 페이지 리디자인', status: 'REVIEW', priority: 'HIGH', who: '김서연', due: '10-02' },
  { n: 11, title: '알림 설정 화면 추가', status: 'IN_PROGRESS', priority: 'MEDIUM', who: '박준호', due: '10-05' },
  { n: 9, title: '결제 오류 문구 정리', status: 'TODO', priority: 'URGENT', who: '이하늘', due: '09-30' },
  { n: 7, title: '온보딩 튜토리얼 초안', status: 'DONE', priority: 'LOW', who: '김서연', due: '09-24' },
];

export function IssueListMock() {
  return (
    <Frame label="프로젝트 이슈 목록: 이슈마다 상태와 담당자가 보이고, 급한 이슈에는 우선순위가 붙는 화면">
      <div className="flex items-center justify-between px-1 pb-2.5">
        <p className="font-semibold">웹 서비스 리뉴얼 · 이슈 4</p>
        <span className="flex items-center gap-1 rounded-md bg-ink px-2.5 py-1 text-[12px] font-medium text-canvas">
          <Icon name="plus" className="size-3.5" />새 이슈
        </span>
      </div>
      <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
        {ISSUES.map((issue) => (
          <li key={issue.n} className="flex items-center gap-2.5 px-3 py-2.5">
            <span className="w-7 shrink-0 font-mono text-[11px] text-ink-faint">#{issue.n}</span>
            <span className="min-w-0 flex-1 truncate">{issue.title}</span>
            <StatusBadge status={issue.status} />
            <span className="hidden sm:contents">
              <PriorityBadge priority={issue.priority} />
            </span>
            <Avatar name={issue.who} size="sm" />
            <span className="hidden w-10 shrink-0 text-right font-mono text-[11px] text-ink-faint sm:block">{issue.due}</span>
          </li>
        ))}
      </ul>
    </Frame>
  );
}

const STATS = [
  ['전체 이슈', 24],
  ['진행 중', 7],
  ['완료', 13],
  ['내 담당', 5],
] as const;

const NOTICES: { icon: IconName; text: string; at: string; unread?: boolean }[] = [
  { icon: 'target', text: '박준호님이 "결제 오류 문구 정리"를 나에게 배정했습니다', at: '방금', unread: true },
  { icon: 'refresh', text: '"로그인 페이지 리디자인"이 검토로 바뀌었습니다', at: '12분 전', unread: true },
  { icon: 'message', text: '이하늘님이 "알림 설정 화면 추가"에 댓글을 남겼습니다', at: '1시간 전' },
];

export function DashboardMock() {
  return (
    <Frame label="대시보드와 알림: 이슈 통계와 나에게 온 알림 목록">
      <dl className="grid grid-cols-4 gap-2">
        {STATS.map(([label, value]) => (
          <div key={label} className="rounded-card border border-line bg-surface px-2.5 py-2">
            <dt className="truncate text-[11px] text-ink-soft">{label}</dt>
            <dd className="mt-0.5 text-[18px] font-semibold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-2.5 overflow-hidden rounded-card border border-line bg-surface">
        <div className="flex items-center gap-2 border-b border-line px-3 py-2">
          <Icon name="bell" className="size-4 text-ink-soft" />
          <p className="flex-1 font-semibold">알림</p>
          <span className="rounded-full bg-danger px-1.5 text-[10px] font-semibold leading-4 text-white">2</span>
        </div>
        <ul className="divide-y divide-line">
          {NOTICES.map((n) => (
            <li key={n.text} className={`flex items-start gap-2.5 px-3 py-2.5 ${n.unread ? 'bg-accent-soft/50' : ''}`}>
              <Icon name={n.icon} className="mt-0.5 size-4 shrink-0 text-ink-soft" />
              <p className="min-w-0 flex-1 text-[12px] leading-relaxed">{n.text}</p>
              <span className="shrink-0 text-[11px] text-ink-faint">{n.at}</span>
            </li>
          ))}
        </ul>
      </div>
    </Frame>
  );
}
