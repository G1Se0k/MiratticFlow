'use client';

import { useState } from 'react';
import { ChatPanel } from '@/components/chat/ChatPanel';
import { Icon } from '@/components/ui/Icon';
import { useProjectChat } from '@/hooks/useChat';

/**
 * 오른쪽 아래에 붙어 있는 프로젝트 채팅.
 * 접으면 동그란 버튼 하나, 펼치면 대화창이 된다.
 *
 * 접었을 때는 ChatPanel 을 아예 그리지 않는다 — WebSocket 연결도 같이 끊긴다.
 * 안 보는 화면 때문에 연결을 붙잡고 있을 이유가 없다.
 * (대신 접은 동안 온 메시지는 다시 펼칠 때 REST 로 받아온다.)
 */
export function ProjectChatDock({ projectId }: { projectId: number }) {
  const [open, setOpen] = useState(false);
  const { data: topic } = useProjectChat(projectId);

  if (!topic) return null;

  return (
    <div className="fixed bottom-4 right-4 z-20 print:hidden">
      {open ? (
        <ChatPanel
          topicId={topic.id}
          title="프로젝트 채팅"
          onClose={() => setOpen(false)}
          className="h-[24rem] w-[19rem] shadow-pop sm:w-[21rem]"
        />
      ) : (
        <button
          onClick={() => setOpen(true)}
          aria-label="프로젝트 채팅 열기"
          className="flex size-10 items-center justify-center rounded-full bg-ink text-canvas shadow-pop transition-transform hover:scale-105"
        >
          <Icon name="message" className="size-4.5" />
        </button>
      )}
    </div>
  );
}
