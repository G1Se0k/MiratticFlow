'use client';

import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { controlClass } from '@/components/ui/FormField';
import { Icon } from '@/components/ui/Icon';
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
      className={`flex flex-col overflow-hidden rounded-card border border-line bg-surface ${className}`}
    >
      <div className="flex items-center gap-2 border-b border-line px-3 py-2">
        <ConnectionDot state={state} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13px] font-medium">{title}</p>
          {subtitle && <p className="truncate text-[11px] text-ink-faint">{subtitle}</p>}
        </div>
        {onClose && (
          <Button size="icon" variant="ghost" onClick={onClose} aria-label="채팅 닫기">
            <Icon name="close" className="size-4" />
          </Button>
        )}
      </div>

      <div className="thin-scroll flex flex-1 flex-col gap-2.5 overflow-y-auto p-3">
        {isPending ? (
          <Spinner />
        ) : messages.length === 0 ? (
          <p className="text-[13px] text-ink-faint">아직 대화가 없습니다.</p>
        ) : (
          messages.map((message) => (
            <MessageRow key={message.id} message={message} isMe={message.senderId === me?.id} />
          ))
        )}
        <div ref={bottomRef} />
      </div>

      <form
        className="flex gap-1.5 border-t border-line p-2"
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
          className={`h-8 min-w-0 flex-1 px-2.5 ${controlClass}`}
        />
        <Button type="submit" size="icon" aria-label="보내기" disabled={draft.trim().length === 0}>
          <Icon name="send" className="size-4" />
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
      <p className="text-center text-[11px] text-ink-faint">
        {message.content} · {time}
      </p>
    );
  }

  return (
    <div className={`flex flex-col gap-0.5 ${isMe ? 'items-end' : 'items-start'}`}>
      <p className="text-[11px] text-ink-faint">
        {isMe ? '나' : message.senderName} · {time}
      </p>
      <p
        className={`max-w-[85%] whitespace-pre-wrap rounded-lg px-2.5 py-1.5 text-[13px] leading-relaxed ${
          isMe ? 'bg-ink text-canvas' : 'bg-raised text-ink'
        }`}
      >
        {message.content}
      </p>
    </div>
  );
}

/** 연결 상태는 글자로 늘 떠 있을 필요가 없다. 점 하나면 충분하고, 이름은 스크린리더에 남긴다. */
function ConnectionDot({ state }: { state: 'connecting' | 'connected' | 'disconnected' }) {
  const label = { connecting: '연결 중', connected: '연결됨', disconnected: '연결 끊김' }[state];
  const color = {
    connecting: 'bg-ink-faint animate-pulse',
    connected: 'bg-done',
    disconnected: 'bg-danger',
  }[state];

  return (
    <span role="status" aria-label={`채팅 ${label}`} title={label} className="flex size-4 items-center justify-center">
      <span className={`size-1.5 rounded-full ${color}`} />
    </span>
  );
}
