'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { useNotificationMutations, useNotifications, useUnreadCount } from '@/hooks/useNotifications';
import type { Notification, NotificationType } from '@/lib/api/notification';

const ICON: Record<NotificationType, string> = {
  ISSUE_ASSIGNED: '🎯',
  ISSUE_STATUS_CHANGED: '🔄',
  COMMENT_ADDED: '💬',
  PROJECT_JOINED: '📁',
};

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const { data: unread = 0 } = useUnreadCount();
  const containerRef = useRef<HTMLDivElement>(null);

  // 바깥을 누르면 닫는다. 목록이 화면을 덮고 있으므로 Esc 도 받는다.
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => setOpen((prev) => !prev)}
        aria-label={unread > 0 ? `알림 ${unread}개` : '알림'}
        aria-expanded={open}
        className="relative flex h-9 w-9 items-center justify-center rounded-md text-lg transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
      >
        🔔
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-medium text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && <NotificationList onClose={() => setOpen(false)} />}
    </div>
  );
}

function NotificationList({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const { data: notifications, isPending } = useNotifications(true);
  const { markRead, markAllRead } = useNotificationMutations();
  const hasUnread = notifications?.some((n) => !n.read) ?? false;

  const open = (notification: Notification) => {
    if (!notification.read) markRead.mutate(notification.id);
    onClose();
    router.push(notification.link);
  };

  return (
    <div className="absolute right-0 top-11 z-30 w-80 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2 dark:border-slate-800">
        <p className="text-sm font-medium">알림</p>
        {hasUnread && (
          <Button size="sm" variant="ghost" onClick={() => markAllRead.mutate()} disabled={markAllRead.isPending}>
            모두 읽음
          </Button>
        )}
      </div>

      <div className="max-h-96 overflow-y-auto">
        {isPending ? (
          <div className="p-6">
            <Spinner />
          </div>
        ) : notifications?.length === 0 ? (
          <p className="p-6 text-center text-sm text-slate-400">아직 알림이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-slate-200 dark:divide-slate-800">
            {notifications?.map((notification) => (
              <li key={notification.id}>
                <button
                  onClick={() => open(notification)}
                  className={`flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-800 ${
                    notification.read ? '' : 'bg-brand-50/60 dark:bg-brand-700/10'
                  }`}
                >
                  <span aria-hidden className="text-base leading-5">
                    {ICON[notification.type]}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={`block text-sm ${notification.read ? 'text-slate-500' : 'font-medium'}`}>
                      {notification.content}
                    </span>
                    <span className="block text-xs text-slate-400">{formatTime(notification.createdAt)}</span>
                  </span>
                  {!notification.read && (
                    <span aria-label="안 읽음" className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-600" />
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/**
 * 오늘이면 시각만, 아니면 날짜까지. 상대 시간("3분 전")은 매초 다시 그려야 해서 두지 않았다.
 *
 * createdAt 은 서버의 LocalDateTime(시간대 없음)이라 브라우저의 "오늘"과 비교한다.
 * toISOString() 은 UTC 라 자정 근처에서 하루가 어긋나므로 쓰지 않는다.
 */
function formatTime(createdAt: string) {
  const now = new Date();
  const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  return createdAt.slice(0, 10) === today
    ? createdAt.slice(11, 16)
    : createdAt.slice(5, 16).replace('T', ' ');
}

const pad = (value: number) => String(value).padStart(2, '0');
