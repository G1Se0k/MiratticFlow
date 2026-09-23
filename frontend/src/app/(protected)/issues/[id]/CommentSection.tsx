'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { useComments, useCommentMutations } from '@/hooks/useComments';
import type { Comment } from '@/lib/api/comment';

const TEXTAREA_CLASS =
  'w-full rounded-md border border-slate-300 bg-white p-3 text-sm outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-slate-400';

export function CommentSection({ issueId }: { issueId: number }) {
  const { data: comments, isPending } = useComments(issueId);
  const { write, edit, remove } = useCommentMutations(issueId);
  const toast = useToast();
  const [draft, setDraft] = useState('');

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-slate-500">댓글 {comments?.length ?? 0}개</h2>

      {isPending ? (
        <Spinner />
      ) : comments?.length === 0 ? (
        <p className="text-sm text-slate-400">아직 댓글이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-3">
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
          className={TEXTAREA_CLASS}
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
    <li className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
      <div className="flex items-center gap-2">
        <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-medium text-brand-700 dark:bg-brand-700/25 dark:text-brand-200">
          {comment.authorName.slice(0, 1)}
        </div>
        <p className="text-sm font-medium">{comment.authorName}</p>
        <time className="text-xs text-slate-400" dateTime={comment.createdAt}>
          {comment.createdAt.slice(0, 16).replace('T', ' ')}
          {/* 수정된 댓글은 그렇다고 알려준다 */}
          {comment.updatedAt !== comment.createdAt && ' (수정됨)'}
        </time>
        {!editing && (comment.canEdit || comment.canDelete) && (
          <div className="ml-auto flex gap-1">
            {comment.canEdit && (
              <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>
                수정
              </Button>
            )}
            {comment.canDelete && (
              <Button size="sm" variant="ghost" onClick={onRemove}>
                삭제
              </Button>
            )}
          </div>
        )}
      </div>

      {editing ? (
        <div className="mt-2 flex flex-col gap-2">
          <textarea rows={3} value={draft} onChange={(e) => setDraft(e.target.value)} className={TEXTAREA_CLASS} />
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
        <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed">{comment.content}</p>
      )}
    </li>
  );
}
