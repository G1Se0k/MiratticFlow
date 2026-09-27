'use client';

import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';

/**
 * <dialog> 를 쓴다. 포커스 가두기, Esc 로 닫기, 배경 클릭 차단이 브라우저 기본으로 들어있어
 * 직접 구현할 게 없다. 열림·닫힘 전환은 globals.css 에서 dialog 선택자로 한 번만 정의한다.
 */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  // 제목을 dialog 의 이름으로 연결한다. 없으면 스크린리더가 "대화상자"라고만 읽는다.
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(e) => {
        // 배경(dialog 자체)을 클릭했을 때만 닫는다. 내용 클릭은 통과시킨다.
        if (e.target === ref.current) onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-[26rem] rounded-card border border-line bg-surface p-0 text-ink shadow-pop"
    >
      <div className="flex items-start gap-3 px-5 pt-4">
        <div className="min-w-0 flex-1">
          <h2 id={titleId} className="text-[15px] font-semibold">
            {title}
          </h2>
          {description && <p className="mt-1 text-[13px] leading-relaxed text-ink-soft">{description}</p>}
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="-mr-1.5 -mt-0.5 flex size-7 items-center justify-center rounded-md text-ink-faint transition-colors hover:bg-raised hover:text-ink"
        >
          <Icon name="close" className="size-4" />
        </button>
      </div>
      <div className="px-5 pb-5 pt-4">{children}</div>
    </dialog>
  );
}

/** 모달 하단 버튼 줄. 어느 모달이든 취소가 왼쪽, 실행이 오른쪽에 오게 맞춘다. */
export function ModalActions({ children }: { children: ReactNode }) {
  return <div className="mt-1 flex items-center justify-end gap-2">{children}</div>;
}
