'use client';

import { useEffect, useRef, type ReactNode } from 'react';

/**
 * <dialog> 를 쓴다. 포커스 가두기, Esc 로 닫기, 배경 클릭 차단이 브라우저 기본으로 들어있어
 * 직접 구현할 게 없다.
 */
export function Modal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => {
        // 배경(dialog 자체)을 클릭했을 때만 닫는다. 내용 클릭은 통과시킨다.
        if (e.target === ref.current) onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl bg-white p-6 text-slate-900 shadow-xl backdrop:bg-slate-900/40 dark:bg-slate-900 dark:text-slate-100"
    >
      <h2 className="mb-4 text-lg font-semibold">{title}</h2>
      {children}
    </dialog>
  );
}
