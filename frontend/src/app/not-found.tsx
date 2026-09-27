import Link from 'next/link';

/** 없는 주소로 들어왔을 때. 기본 404 대신 돌아갈 길이 있는 화면을 보여준다. */
export default function NotFound() {
  return (
    <div className="flex min-h-[60dvh] flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="font-mono text-3xl tracking-tight text-ink-faint">404</p>
      <h1 className="text-[15px] font-semibold">페이지를 찾을 수 없습니다</h1>
      <p className="max-w-sm text-[13px] text-ink-soft">주소가 바뀌었거나 삭제된 페이지일 수 있습니다.</p>
      <Link
        href="/workspaces"
        className="mt-1 inline-flex h-9 items-center rounded-md bg-ink px-3.5 text-[13px] font-medium text-canvas transition-opacity hover:opacity-90"
      >
        워크스페이스로
      </Link>
    </div>
  );
}
