/**
 * 내용이 들어올 자리를 미리 잡아 두는 회색 블록.
 *
 * 스피너는 높이가 없어서 목록이 도착하는 순간 아래 내용이 밀린다(레이아웃 이동).
 * 목록·카드처럼 **모양을 아는 자리**는 스켈레톤이 낫고,
 * 크기를 모르는 작은 영역은 그대로 스피너를 쓴다.
 */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-slate-200 dark:bg-slate-800 ${className}`} aria-hidden />;
}

/** 목록 자리. 몇 줄을 잡아 둘지는 쓰는 쪽이 정한다. */
export function SkeletonList({ rows = 3, className = 'h-16' }: { rows?: number; className?: string }) {
  return (
    <div className="flex flex-col gap-2" role="status" aria-label="불러오는 중">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className={className} />
      ))}
    </div>
  );
}
