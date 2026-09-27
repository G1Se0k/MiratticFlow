'use client';

import { useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { controlClass } from '@/components/ui/FormField';
import { Menu } from '@/components/ui/Menu';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { useComments, useCommentMutations } from '@/hooks/useComments';
import type { Comment } from '@/lib/api/comment';

export function CommentSection({ issueId }: { issueId: number }) {
  const { data: comments, isPending } = useComments(issueId);
  const { write, edit, remove } = useCommentMutations(issueId);
  const toast = useToast();
  const [draft, setDraft] = useState('');

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-[13px] font-semibold">댓글 {comments?.length ?? 0}</h2>

      {isPending ? (
        <Spinner />
      ) : comments?.length === 0 ? (
        <p className="text-[13px] text-ink-faint">아직 댓글이 없습니다.</p>
      ) : (
        <ul className="flex flex-col">
          {comments?.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              onEdit={(content) =>
                edit.mutate(
                  { id: comment.id, content },
                  { onError: (error) => toast(error.message, 'error') },
                )
              }
              onRemove={() =>
                remove.mutate(comment.id, {
                  onSuccess: () => toast('댓글을 삭제했습니다.'),
                  onError: (error) => toast(error.message, 'error'),
                })
              }
            />
          ))}
        </ul>
      )}

      <form
        className="flex flex-col gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          write.mutate(draft.trim(), {
            onSuccess: () => setDraft(''),
            onError: (error) => toast(error.message, 'error'),
          });
        }}
      >
        <label htmlFor="new-comment" className="sr-only">
          댓글 작성
        </label>
        <textarea
          id="new-comment"
          rows={3}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="댓글을 남겨보세요."
          className={`resize-y px-2.5 py-2 leading-relaxed ${controlClass}`}
        />
        <div className="flex justify-end">
          <Button type="submit" size="sm" disabled={write.isPending || draft.trim().length === 0}>
            등록
          </Button>
        </div>
      </form>
    </section>
  );
}

/** 카드 대신 한 줄 구분선으로 잇는다. 댓글이 쌓일수록 카드는 화면을 조각낸다. */
function CommentItem({
  comment,
  onEdit,
  onRemove,
}: {
  comment: Comment;
  onEdit: (content: string) => void;
  onRemove: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(comment.content);

  return (
    <li className="flex gap-2.5 border-b border-line py-3 first:pt-0 last:border-b-0">
      <Avatar name={comment.authorName} size="sm" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-[13px] font-medium">{comment.authorName}</p>
          <time className="text-[11px] text-ink-faint" dateTime={comment.createdAt}>
            {comment.createdAt.slice(0, 16).replace('T', ' ')}
            {/* 수정된 댓글은 그렇다고 알려준다 */}
            {comment.updatedAt !== comment.createdAt && ' (수정됨)'}
          </time>
          {!editing && (comment.canEdit || comment.canDelete) && (
            <div className="ml-auto">
              <Menu
                label="댓글 관리"
                items={[
                  ...(comment.canEdit
                    ? [{ label: '수정', icon: 'settings' as const, onSelect: () => setEditing(true) }]
                    : []),
                  ...(comment.canDelete
                    ? [{ label: '삭제', icon: 'trash' as const, onSelect: onRemove, danger: true }]
                    : []),
                ]}
              />
            </div>
          )}
        </div>

        {editing ? (
          <div className="mt-2 flex flex-col gap-2">
            <textarea
              rows={3}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              className={`resize-y px-2.5 py-2 leading-relaxed ${controlClass}`}
            />
            <div className="flex justify-end gap-2">
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setDraft(comment.content);
                  setEditing(false);
                }}
              >
                취소
              </Button>
              <Button
                size="sm"
                disabled={draft.trim().length === 0}
                onClick={() => {
                  onEdit(draft.trim());
                  setEditing(false);
                }}
              >
                저장
              </Button>
            </div>
          </div>
        ) : (
          <p className="mt-1 whitespace-pre-wrap text-[13px] leading-relaxed text-ink-soft">{comment.content}</p>
        )}
      </div>
    </li>
  );
}
