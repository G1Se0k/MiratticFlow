'use client';

import { useRouter } from 'next/navigation';
import { useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Icon, type IconName } from '@/components/ui/Icon';
import { Spinner } from '@/components/ui/Spinner';
import { useDismiss } from '@/hooks/useDismiss';
import { useNotificationMutations, useNotifications, useUnreadCount } from '@/hooks/useNotifications';
import type { Notification, NotificationType } from '@/lib/api/notification';

const ICON: Record<NotificationType, IconName> = {
  ISSUE_ASSIGNED: 'target',
  ISSUE_STATUS_CHANGED: 'refresh',
  COMMENT_ADDED: 'message',
  PROJECT_JOINED: 'folder',
};

/**
 * 목록이 나타나는 위치는 쓰는 쪽이 정한다.
 * 사이드바 맨 아래에서는 위로, 모바일 상단바에서는 아래로 펼쳐야 화면을 벗어나지 않는다.
 */
export function NotificationBell({ panelClass = 'right-0 top-10' }: { panelClass?: string }) {
  const [open, setOpen] = useState(false);
  const { data: unread = 0 } = useUnreadCount();
  const containerRef = useRef<HTMLDivElement>(null);

  useDismiss(open, containerRef, () => setOpen(false));

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => setOpen((prev) => !prev)}
        aria-label={unread > 0 ? `알림 ${unread}개` : '알림'}
        aria-expanded={open}
        className="relative flex size-8 items-center justify-center rounded-md text-ink-soft transition-colors hover:bg-raised hover:text-ink"
      >
        <Icon name="bell" className="size-4.5" />
        {unread > 0 && (
          <span className="absolute right-0.5 top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && <NotificationList panelClass={panelClass} onClose={() => setOpen(false)} />}
    </div>
  );
}

function NotificationList({ panelClass, onClose }: { panelClass: string; onClose: () => void }) {
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
    <div
      className={`absolute z-30 w-80 overflow-hidden rounded-card border border-line bg-surface shadow-pop ${panelClass}`}
    >
      <div className="flex items-center justify-between border-b border-line px-3 py-2">
        <p className="text-[13px] font-semibold">알림</p>
        {hasUnread && (
          <Button size="sm" variant="ghost" onClick={() => markAllRead.mutate()} disabled={markAllRead.isPending}>
            모두 읽음
          </Button>
        )}
      </div>

      <div className="thin-scroll max-h-96 overflow-y-auto">
        {isPending ? (
          <Spinner />
        ) : notifications?.length === 0 ? (
          <p className="px-3 py-8 text-center text-[13px] text-ink-faint">아직 알림이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-line">
            {notifications?.map((notification) => (
              <li key={notification.id}>
                <button
                  onClick={() => open(notification)}
                  className={`flex w-full gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-raised ${
                    notification.read ? '' : 'bg-accent-soft/60'
                  }`}
                >
                  <Icon
                    name={ICON[notification.type]}
                    className={`mt-0.5 size-4 ${notification.read ? 'text-ink-faint' : 'text-accent'}`}
                  />
                  <span className="min-w-0 flex-1">
                    <span
                      className={`block text-[13px] leading-snug ${notification.read ? 'text-ink-soft' : 'font-medium'}`}
                    >
                      {notification.content}
                    </span>
                    <span className="mt-0.5 block text-[11px] text-ink-faint">
                      {formatTime(notification.createdAt)}
                    </span>
                  </span>
                  {!notification.read && (
                    <span aria-label="안 읽음" className="mt-1.5 size-1.5 shrink-0 rounded-full bg-accent" />
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
