import Link from 'next/link';

/** 없는 주소로 들어왔을 때. 기본 404 대신 돌아갈 길이 있는 화면을 보여준다. */
export default function NotFound() {
  return (
    <div className="flex min-h-[60dvh] flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="font-mono text-4xl text-slate-300 dark:text-slate-700">404</p>
      <h1 className="text-lg font-semibold">페이지를 찾을 수 없습니다</h1>
      <p className="max-w-sm text-sm text-slate-500">주소가 바뀌었거나 삭제된 페이지일 수 있습니다.</p>
      <Link
        href="/workspaces"
        className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
      >
        워크스페이스로
      </Link>
    </div>
  );
}
