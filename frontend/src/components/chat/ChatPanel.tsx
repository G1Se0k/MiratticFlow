'use client';

import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { useMe } from '@/hooks/useAuth';
import { useChat } from '@/hooks/useChat';
import type { ChatMessage } from '@/lib/api/chat';

/**
 * 주제 하나의 대화창. 프로젝트 채팅(오른쪽 아래 독)과 이슈 주제(분할 창)가 같은 것을 쓴다.
 * 높이는 쓰는 쪽에서 className 으로 정한다.
 */
export function ChatPanel({
  topicId,
  title,
  subtitle,
  onClose,
  className = '',
}: {
  topicId: number;
  title: string;
  subtitle?: string | null;
  onClose?: () => void;
  className?: string;
}) {
  const { messages, isPending, state, send } = useChat(topicId);
  const { data: me } = useMe();
  const toast = useToast();
  const [draft, setDraft] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지가 오면 맨 아래로 따라 내려간다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  return (
    <section
      className={`flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 ${className}`}
    >
      <div className="flex items-center gap-2 border-b border-slate-200 px-3 py-2 dark:border-slate-800">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{title}</p>
          {subtitle && <p className="truncate text-xs text-slate-500">{subtitle}</p>}
        </div>
        <ConnectionBadge state={state} />
        {onClose && (
          <Button size="sm" variant="ghost" onClick={onClose} aria-label="채팅 닫기">
            ✕
          </Button>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 overflow-y-auto p-3">
        {isPending ? (
          <Spinner />
        ) : messages.length === 0 ? (
          <p className="text-sm text-slate-400">아직 대화가 없습니다.</p>
        ) : (
          messages.map((message) => (
            <MessageRow key={message.id} message={message} isMe={message.senderId === me?.id} />
          ))
        )}
        <div ref={bottomRef} />
      </div>

      <form
        className="flex gap-2 border-t border-slate-200 p-2 dark:border-slate-800"
        onSubmit={(e) => {
          e.preventDefault();
          if (state !== 'connected') return toast('연결 중입니다. 잠시 후 다시 시도해주세요.', 'error');
          send(draft.trim());
          setDraft('');
        }}
      >
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="메시지를 입력하세요"
          aria-label="메시지 입력"
          className="h-9 min-w-0 flex-1 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-slate-400"
        />
        <Button type="submit" size="sm" disabled={draft.trim().length === 0}>
          보내기
        </Button>
      </form>
    </section>
  );
}

/** 시스템 메시지는 말풍선이 아니라 가운데 한 줄로 흘려 보낸다. */
function MessageRow({ message, isMe }: { message: ChatMessage; isMe: boolean }) {
  const time = message.createdAt.slice(11, 16);

  if (message.type === 'SYSTEM') {
    return (
      <p className="text-center text-xs text-slate-400">
        {message.content} · {time}
      </p>
    );
  }

  return (
    <div className={`flex flex-col gap-0.5 ${isMe ? 'items-end' : 'items-start'}`}>
      <p className="text-xs text-slate-500">
        {isMe ? '나' : message.senderName} · {time}
      </p>
      <p
        className={`max-w-[85%] whitespace-pre-wrap rounded-xl px-3 py-2 text-sm ${
          isMe ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100'
        }`}
      >
        {message.content}
      </p>
    </div>
  );
}

function ConnectionBadge({ state }: { state: 'connecting' | 'connected' | 'disconnected' }) {
  const label = { connecting: '연결 중', connected: '연결됨', disconnected: '연결 끊김' }[state];
  const tone = {
    connecting: 'text-slate-400',
    connected: 'text-emerald-600 dark:text-emerald-400',
    disconnected: 'text-red-600 dark:text-red-400',
  }[state];

  return (
    <span className={`ml-auto shrink-0 text-xs ${tone}`} role="status">
      {label}
    </span>
  );
}
