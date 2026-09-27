import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/** 점선 테두리 대신 아이콘 하나와 짧은 안내만 둔다. 비어 있는 상태가 고장처럼 보이지 않게. */
export function EmptyState({
  icon = 'inbox',
  title,
  description,
  action,
}: {
  icon?: IconName;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <span className="flex size-9 items-center justify-center rounded-full bg-raised text-ink-faint">
        <Icon name={icon} className="size-4.5" />
      </span>
      <p className="text-[13px] font-medium">{title}</p>
      {description && <p className="max-w-xs text-[13px] leading-relaxed text-ink-soft">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
