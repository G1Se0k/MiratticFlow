'use client';

import { useEffect, type RefObject } from 'react';

/**
 * 열려 있는 팝오버를 바깥 클릭과 Esc 로 닫는다.
 * 알림 목록과 워크스페이스 전환기가 같은 동작을 해야 해서 한 곳에 둔다.
 */
export function useDismiss(open: boolean, ref: RefObject<HTMLElement | null>, close: () => void) {
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) close();
    };
    const onKeyDown = (e: KeyboardEvent) => e.key === 'Escape' && close();
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, ref, close]);
}
