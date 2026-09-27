export function Spinner({ label = '불러오는 중' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-8 text-[13px] text-ink-faint" role="status">
      <span className="size-3.5 animate-spin rounded-full border-2 border-line-strong border-t-ink" />
      {label}
    </div>
  );
}
