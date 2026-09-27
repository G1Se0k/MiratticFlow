'use client';

import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { Icon } from './Icon';

type Toast = { id: number; message: string; tone: 'success' | 'error' };

const ToastContext = createContext<(message: string, tone?: Toast['tone']) => void>(() => {});

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const show = useCallback((message: string, tone: Toast['tone'] = 'success') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, tone }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 3000);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      {/* 화면 가운데는 보던 내용을 가린다. 오른쪽 아래로 보내고 aria-live 로 스크린리더에도 전달한다. */}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col items-end gap-2">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="flex max-w-80 items-start gap-2 rounded-md border border-line bg-surface px-3 py-2 text-[13px] text-ink shadow-pop"
          >
            <Icon
              name={toast.tone === 'error' ? 'alert' : 'check'}
              className={`mt-px size-4 ${toast.tone === 'error' ? 'text-danger' : 'text-done'}`}
            />
            <span className="min-w-0">{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
