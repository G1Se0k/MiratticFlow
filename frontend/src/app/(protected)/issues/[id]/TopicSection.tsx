'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { FormField } from '@/components/ui/FormField';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/Toast';
import { useTopicMutations, useTopics } from '@/hooks/useChat';
import type { Topic } from '@/lib/api/chat';

/**
 * 이슈 안에서 만드는 대화 주제 목록.
 * 주제를 누르면 페이지를 떠나지 않고 오른쪽 창에 대화가 열린다(열고 닫는 건 페이지가 관리한다).
 */
export function TopicSection({
  issueId,
  openTopicId,
  onOpen,
}: {
  issueId: number;
  openTopicId: number | null;
  onOpen: (topic: Topic | null) => void;
}) {
  const { data: topics } = useTopics(issueId);
  const { remove } = useTopicMutations(issueId);
  const toast = useToast();
  const [creating, setCreating] = useState(false);

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-slate-500">주제 {topics?.length ?? 0}개</h2>
        <Button size="sm" variant="secondary" onClick={() => setCreating(true)}>
          주제 만들기
        </Button>
      </div>

      {topics?.length === 0 ? (
        <p className="text-sm text-slate-400">
          이 이슈로 따로 이야기할 주제를 만들 수 있습니다.
        </p>
      ) : (
        <ul className="divide-y divide-slate-200 rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {topics?.map((topic) => (
            <li key={topic.id} className="flex items-center gap-3 px-4 py-3">
              <button
                onClick={() => onOpen(topic.id === openTopicId ? null : topic)}
                aria-current={topic.id === openTopicId ? 'true' : undefined}
                className={`min-w-0 flex-1 text-left ${topic.id === openTopicId ? 'text-brand-700 dark:text-brand-300' : ''}`}
              >
                <p className="truncate text-sm font-medium"># {topic.name}</p>
                <p className="truncate text-xs text-slate-500">
                  {topic.description || '설명 없음'} · {topic.createdByName}
                </p>
              </button>
              {topic.canManage && (
                <Button
                  size="sm"
                  variant="danger"
                  className="shrink-0"
                  onClick={() =>
                    remove.mutate(topic.id, {
                      onSuccess: () => {
                        if (topic.id === openTopicId) onOpen(null);
                        toast('주제를 삭제했습니다.');
                      },
                      onError: (error) => toast(error.message, 'error'),
                    })
                  }
                >
                  삭제
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <CreateTopicModal
        open={creating}
        onClose={() => setCreating(false)}
        issueId={issueId}
        onCreated={onOpen}
      />
    </section>
  );
}

function CreateTopicModal({
  open,
  onClose,
  issueId,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  issueId: number;
  onCreated: (topic: Topic) => void;
}) {
  const { create } = useTopicMutations(issueId);
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
                onCreated(topic);
                onClose();
              },
              onError: (error) => toast(error.message, 'error'),
            },
          );
        }}
      >
        <FormField label="이름" placeholder="예: 재현 방법" value={name} onChange={(e) => setName(e.target.value)} />
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
