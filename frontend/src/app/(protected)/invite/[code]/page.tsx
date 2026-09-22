'use client';

import { useParams, useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { LogoBlock } from '@/components/ui/Logo';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { workspaceApi } from '@/lib/api/workspace';
import { useState } from 'react';

/**
 * 초대를 열자마자 참여시키지 않는다.
 * 어디에 들어가는지 보여주고 사용자가 직접 누르게 한다.
 */
export default function InvitePage() {
  const { code } = useParams<{ code: string }>();
  const router = useRouter();
  const toast = useToast();
  const [joining, setJoining] = useState(false);

  const { data: preview, isPending, isError, error } = useQuery({
    queryKey: ['invite', code],
    queryFn: () => workspaceApi.previewInvite(code),
    retry: false,
  });

  if (isPending) return <Spinner label="초대를 확인하는 중..." />;

  if (isError || !preview) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <LogoBlock />
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error?.message ?? '초대를 확인할 수 없습니다.'}
        </p>
        <Button variant="secondary" onClick={() => router.push('/workspaces')}>
          내 워크스페이스로
        </Button>
      </div>
    );
  }

  const join = async () => {
    setJoining(true);
    try {
      const { workspaceId } = await workspaceApi.acceptInvite(code);
      toast(`${preview.workspaceName}에 참여했습니다.`);
      router.replace(`/workspaces/${workspaceId}`);
    } catch (e) {
      toast(e instanceof Error ? e.message : '참여하지 못했습니다.', 'error');
      setJoining(false);
    }
  };

  return (
    <div className="flex flex-col items-center gap-6 py-12 text-center">
      <LogoBlock />
      <div>
        <p className="text-sm text-slate-500">다음 워크스페이스에 초대되었습니다</p>
        <p className="mt-1 text-2xl font-semibold tracking-tight">{preview.workspaceName}</p>
      </div>

      {preview.alreadyMember ? (
        <>
          <p className="text-sm text-slate-500">이미 참여 중인 워크스페이스입니다.</p>
          <Button onClick={() => router.replace(`/workspaces/${preview.workspaceId}`)}>워크스페이스로 이동</Button>
        </>
      ) : (
        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => router.push('/workspaces')}>
            나중에
          </Button>
          <Button onClick={join} disabled={joining}>
            {joining ? '참여하는 중...' : '참여하기'}
          </Button>
        </div>
      )}
    </div>
  );
}
