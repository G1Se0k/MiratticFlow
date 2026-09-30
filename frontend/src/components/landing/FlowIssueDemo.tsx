'use client';

import { useEffect, useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { PriorityBadge, StatusBadge } from '@/components/ui/Badge';

/** 이슈 채팅에 차례로 올라오는 대화. 마지막 줄(검토 요청)과 함께 이슈가 '검토'로 넘어간다. */
const LINES = [
  { who: '박준호', at: '20:08', text: '소셜 버튼 높이는 44px로 통일할까요?' },
  { who: '나', at: '20:10', text: '네, 카카오·네이버·Google 모두 44로 맞춰주세요.' },
  { who: '이하늘', at: '20:24', text: '에러 문구 자리도 미리 비워둘게요.' },
  { who: '나', at: '20:27', text: '좋아요. 반영되면 검토 부탁드려요.' },
  { who: '박준호', at: '20:41', text: '올렸습니다. 검토 부탁드려요!' },
];
const SHOWN = 4;

/**
 * 랜딩 첫 화면의 그림: 이슈 화면과 그 옆 주제 채팅 (issues/[id] 와 ChatPanel 을 흉내 낸다 — 바뀌면 같이 고친다).
 * 서버 렌더와 자바스크립트 없는 화면은 마지막 장면이고, 불러온 뒤 대화를 처음부터 다시 보여 준다.
 * 움직임 줄이기를 켠 사람(도중에 켜도)에게는 마지막 장면만 보인다.
 */
export function FlowIssueDemo() {
  const [count, setCount] = useState(LINES.length);

  useEffect(() => {
    const reduce = matchMedia('(prefers-reduced-motion: reduce)');
    let timer = 0;
    let i = 0;
    const step = () => {
      setCount(++i);
      timer = window.setTimeout(i < LINES.length ? step : start, i < LINES.length ? 1600 : 4000);
    };
    const start = () => {
      i = 0;
      setCount(0);
      timer = window.setTimeout(step, 400);
    };
    const apply = () => {
      clearTimeout(timer);
      if (reduce.matches) setCount(LINES.length);
      else timer = window.setTimeout(start, 0);
    };
    timer = window.setTimeout(apply, 1200); // 처음엔 마지막 장면을 잠깐 보여 주고 시작한다.
    reduce.addEventListener('change', apply);
    return () => {
      clearTimeout(timer);
      reduce.removeEventListener('change', apply);
    };
  }, []);

  const review = count === LINES.length;

  return (
    <div
      role="img"
      aria-label="Mirattic Flow 이슈 화면: 이슈 정보 옆 주제 채팅에서 대화가 이어지고, 검토 요청과 함께 상태가 검토로 바뀌는 모습"
      className="grid gap-3 rounded-[12px] border border-line bg-canvas p-3.5 text-[13px] shadow-[0_1px_3px_rgb(0_0_0/0.04),0_24px_56px_-28px_rgb(0_23_51/0.2)] sm:grid-cols-[1fr_44%]"
    >
      <div aria-hidden className="flex min-w-0 flex-col gap-3 p-1">
        <span className="text-[11px] text-ink-faint">← 웹 서비스 리뉴얼</span>
        <p className="text-[16px] font-semibold tracking-[-0.015em]">
          <span className="mr-1.5 font-mono text-[12px] font-normal text-ink-faint">#12</span>
          로그인 페이지 리디자인
        </p>
        <dl className="grid grid-cols-2 gap-x-3 gap-y-2.5 rounded-card border border-line bg-surface px-3 py-2.5">
          <Field label="상태">
            <StatusBadge status={review ? 'REVIEW' : 'IN_PROGRESS'} />
          </Field>
          <Field label="우선순위">
            <PriorityBadge priority="HIGH" />
          </Field>
          <Field label="담당자">
            <Avatar name="김서연" size="sm" />
            김서연
          </Field>
          <Field label="마감일">2026-10-02</Field>
        </dl>
        <div>
          <p className="font-semibold">설명</p>
          <p className="mt-1 text-ink-soft">시안 확정 후 컴포넌트 교체. 소셜 로그인 버튼 정렬도 함께 봅니다.</p>
        </div>
      </div>

      <div aria-hidden className="flex h-80 flex-col overflow-hidden rounded-card border border-line bg-surface">
        <div className="flex items-center gap-2 border-b border-line px-3 py-2">
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium"># 시안 확정</p>
            <p className="truncate text-[11px] text-ink-faint">2안 기준으로 세부 조정</p>
          </div>
          <span className="size-1.5 rounded-full bg-done" />
        </div>
        {/* 높이가 정해져 있어 새 줄이 오면 옛 줄이 위로 밀려난다 (화면이 늘어나지 않는다). */}
        <div className="flex min-h-0 flex-1 flex-col justify-end gap-2 overflow-hidden p-2.5 [mask-image:linear-gradient(to_bottom,transparent,black_2.5rem)]">
          {LINES.slice(Math.max(0, count - SHOWN), count).map((line) => {
            const me = line.who === '나';
            return (
              <div
                key={line.at}
                className={`flex flex-col gap-0.5 transition-[opacity,translate] duration-300 starting:translate-y-1.5 starting:opacity-0 motion-reduce:transition-none ${me ? 'items-end' : 'items-start'}`}
              >
                <p className="text-[10px] text-ink-faint">
                  {line.who} · {line.at}
                </p>
                <p className={`max-w-[88%] rounded-lg px-2.5 py-1.5 text-[12px] leading-relaxed ${me ? 'bg-ink text-canvas' : 'bg-raised text-ink'}`}>
                  {line.text}
                </p>
              </div>
            );
          })}
        </div>
        <div className="border-t border-line p-2">
          <p className="rounded-md border border-line px-2.5 py-1.5 text-[12px] text-ink-faint">메시지를 입력하세요</p>
        </div>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] text-ink-soft">{label}</dt>
      <dd className="mt-0.5 flex items-center gap-1.5">{children}</dd>
    </div>
  );
}
