'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { useMe } from '@/hooks/useAuth';
import { useChat, useTopicMutations, useTopics } from '@/hooks/useChat';
import { useProject } from '@/hooks/useProjects';
import type { ChatMessage, Topic } from '@/lib/api/chat';

export default function ProjectChatPage() {
  const projectId = Number(useParams<{ id: string }>().id);
  const { data: project } = useProject(projectId);
  const { data: topics, isPending } = useTopics(projectId);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  // 처음 들어오면 첫 주제("일반")를 연다.
  const topicId = selectedId ?? topics?.[0]?.id ?? 0;
  const topic = topics?.find((t) => t.id === topicId);

  if (isPending) return <Spinner />;

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <Link
          href={`/projects/${projectId}`}
          className="w-fit text-xs text-slate-500 hover:text-brand-600 dark:hover:text-brand-300"
        >
          ← {project?.name ?? '프로젝트'}
        </Link>
        <h1 className="text-xl font-semibold tracking-tight">채팅</h1>
      </header>

      <div className="grid gap-4 sm:grid-cols-[200px_1fr]">
        <aside className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <h2 className="text-xs font-medium text-slate-500">주제 {topics?.length ?? 0}개</h2>
            <Button size="sm" variant="ghost" onClick={() => setCreating(true)}>
              + 추가
            </Button>
          </div>
          <ul className="flex flex-col gap-1">
            {topics?.map((t) => (
              <li key={t.id}>
                <button
                  onClick={() => setSelectedId(t.id)}
                  aria-current={t.id === topicId ? 'true' : undefined}
                  className={`w-full truncate rounded-md px-3 py-2 text-left text-sm transition-colors ${
                    t.id === topicId
                      ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-700/20 dark:text-brand-200'
                      : 'hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  # {t.name}
                </button>
              </li>
            ))}
          </ul>
        </aside>

        {topicId > 0 && topic && <ChatPanel key={topicId} topic={topic} projectId={projectId} />}
      </div>

      <CreateTopicModal
        open={creating}
        onClose={() => setCreating(false)}
        projectId={projectId}
        onCreated={setSelectedId}
      />
    </div>
  );
}

function ChatPanel({ topic, projectId }: { topic: Topic; projectId: number }) {
  const { messages, isPending, state, send } = useChat(topic.id);
  const { remove } = useTopicMutations(projectId);
  const { data: me } = useMe();
  const toast = useToast();
  const [draft, setDraft] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지가 오면 맨 아래로 따라 내려간다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  return (
    <section className="flex min-h-[28rem] flex-col rounded-xl border border-slate-200 dark:border-slate-800">
      <div className="flex items-center gap-2 border-b border-slate-200 px-4 py-3 dark:border-slate-800">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium"># {topic.name}</p>
          {topic.description && <p className="truncate text-xs text-slate-500">{topic.description}</p>}
        </div>
        <ConnectionBadge state={state} />
        {topic.canManage && (
          <Button
            size="sm"
            variant="ghost"
            onClick={() =>
              remove.mutate(topic.id, {
                onSuccess: () => toast('주제를 삭제했습니다.'),
                onError: (error) => toast(error.message, 'error'),
              })
            }
          >
            삭제
          </Button>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 overflow-y-auto p-4" style={{ maxHeight: '28rem' }}>
        {isPending ? (
          <Spinner />
        ) : messages.length === 0 ? (
          <p className="text-sm text-slate-400">아직 대화가 없습니다.</p>
        ) : (
          messages.map((message) => <MessageRow key={message.id} message={message} isMe={message.senderId === me?.id} />)
        )}
        <div ref={bottomRef} />
      </div>

      <form
        className="flex gap-2 border-t border-slate-200 p-3 dark:border-slate-800"
        onSubmit={(e) => {
          e.preventDefault();
          if (state !== 'connected') return toast('연결 중입니다. 잠시 후 다시 시도해주세요.', 'error');
          send(draft.trim());
          setDraft('');
        }}
      >
        <label htmlFor="chat-input" className="sr-only">
          메시지 입력
        </label>
        <input
          id="chat-input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="메시지를 입력하세요"
          className="h-10 flex-1 rounded-md border border-slate-300 bg-white px-3 text-sm outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-slate-400"
        />
        <Button type="submit" disabled={draft.trim().length === 0}>
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
        className={`max-w-[80%] whitespace-pre-wrap rounded-xl px-3 py-2 text-sm ${
          isMe
            ? 'bg-brand-600 text-white'
            : 'bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100'
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

function CreateTopicModal({
  open,
  onClose,
  projectId,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  projectId: number;
  onCreated: (id: number) => void;
}) {
  const { create } = useTopicMutations(projectId);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const toast = useToast();

  return (
    <Modal open={open} onClose={onClose} title="새 주제">
      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate(
            { name: name.trim(), description: description.trim() || undefined },
            {
              onSuccess: (topic) => {
                setName('');
                setDescription('');
                onCreated(topic.id);
                onClose();
              },
              onError: (error) => toast(error.message, 'error'),
            },
          );
        }}
      >
        <FormField label="이름" placeholder="예: 배포" value={name} onChange={(e) => setName(e.target.value)} />
        <FormField
          label="설명 (선택)"
          placeholder="어떤 이야기를 나누나요?"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={create.isPending || name.trim().length === 0}>
            만들기
          </Button>
        </div>
      </form>
    </Modal>
  );
}
