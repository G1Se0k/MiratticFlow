import type { ReactNode } from 'react';
import { Icon } from './Icon';

const TONE = {
  info: { box: 'border-line bg-raised text-ink-soft', icon: 'inbox', mark: 'text-ink-faint' },
  success: { box: 'border-done/25 bg-done-soft text-done', icon: 'check', mark: 'text-done' },
  error: { box: 'border-danger/25 bg-danger-soft text-danger', icon: 'alert', mark: 'text-danger' },
} as const;

/** 화면 위쪽에 한 줄로 붙는 안내 (가입 완료, 비밀번호 변경, 초대 오류 등). */
export function Notice({ tone = 'info', children }: { tone?: keyof typeof TONE; children: ReactNode }) {
  const style = TONE[tone];
  return (
    <p className={`flex items-start gap-2 rounded-md border px-2.5 py-2 text-[13px] leading-relaxed ${style.box}`}>
      <Icon name={style.icon} className={`mt-px size-4 ${style.mark}`} />
      <span className="min-w-0">{children}</span>
    </p>
  );
}
