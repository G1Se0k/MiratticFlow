'use client';

import { useRef, useState } from 'react';
import { useDismiss } from '@/hooks/useDismiss';
import { Icon, type IconName } from './Icon';

export interface MenuItem {
  label: string;
  icon: IconName;
  onSelect: () => void;
  danger?: boolean;
}

/**
 * 헤더의 "⋯" 메뉴.
 * 삭제·나가기 같은 위험한 동작을 화면에 빨간 버튼으로 늘어놓지 않고 여기에 접어 둔다 —
 * 자주 쓰는 동작과 한 번 쓰는 동작이 같은 크기로 놓여 있으면 실수로 누르기 쉽다.
 */
export function Menu({ items, label = '더보기' }: { items: MenuItem[]; label?: string }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useDismiss(open, ref, () => setOpen(false));

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((prev) => !prev)}
        aria-label={label}
        aria-expanded={open}
        className="flex size-8 items-center justify-center rounded-md border border-line bg-surface text-ink-soft transition-colors hover:bg-raised hover:text-ink"
      >
        <Icon name="more" className="size-4" />
      </button>

      {open && (
        <div className="absolute right-0 top-9 z-30 w-48 overflow-hidden rounded-card border border-line bg-surface py-1 shadow-pop">
          {items.map((item) => (
            <button
              key={item.label}
              onClick={() => {
                setOpen(false);
                item.onSelect();
              }}
              className={`flex w-full items-center gap-2 px-2.5 py-1.5 text-left text-[13px] transition-colors hover:bg-raised ${
                item.danger ? 'text-danger' : 'text-ink'
              }`}
            >
              <Icon name={item.icon} className="size-4" />
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
