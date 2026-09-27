'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { FormField } from '@/components/ui/FormField';
import { Icon } from '@/components/ui/Icon';
import { Modal, ModalActions } from '@/components/ui/Modal';
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
    <section className="flex flex-col gap-2.5">
      <div className="flex items-center justify-between">
        <h2 className="text-[13px] font-semibold">주제 {topics?.length ?? 0}</h2>
        <Button size="sm" variant="secondary" onClick={() => setCreating(true)}>
          <Icon name="plus" className="size-3.5" />
          주제 만들기
        </Button>
      </div>

      {topics?.length === 0 ? (
        <p className="text-[13px] text-ink-faint">이 이슈로 따로 이야기할 주제를 만들 수 있습니다.</p>
      ) : (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {topics?.map((topic) => {
            const active = topic.id === openTopicId;
            return (
              <li key={topic.id} className={`flex items-center gap-2 px-3 py-2.5 ${active ? 'bg-accent-soft' : ''}`}>
                <button
                  onClick={() => onOpen(active ? null : topic)}
                  aria-current={active ? 'true' : undefined}
                  className="flex min-w-0 flex-1 items-center gap-2 text-left"
                >
                  <Icon name="message" className={`size-4 ${active ? 'text-accent' : 'text-ink-faint'}`} />
                  <span className="min-w-0">
                    <span className={`block truncate text-[13px] font-medium ${active ? 'text-accent-ink' : ''}`}>
                      {topic.name}
                    </span>
                    <span className="block truncate text-xs text-ink-soft">
                      {topic.description || '설명 없음'} · {topic.createdByName}
                    </span>
                  </span>
                </button>
                {topic.canManage && (
                  <Button
                    size="icon"
                    variant="danger"
                    aria-label={`${topic.name} 주제 삭제`}
                    onClick={() =>
                      remove.mutate(topic.id, {
                        onSuccess: () => {
                          if (active) onOpen(null);
                          toast('주제를 삭제했습니다.');
                        },
                        onError: (error) => toast(error.message, 'error'),
                      })
                    }
                  >
                    <Icon name="trash" className="size-3.5" />
                  </Button>
                )}
              </li>
            );
          })}
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
        className="flex flex-col gap-3.5"
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
        <ModalActions>
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" size="sm" disabled={create.isPending || name.trim().length === 0}>
            만들기
          </Button>
        </ModalActions>
      </form>
    </Modal>
  );
}
