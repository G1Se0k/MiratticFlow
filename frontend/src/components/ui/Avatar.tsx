/** 이름 첫 글자 원형. 멤버 목록·채팅·사이드바가 같은 것을 쓴다. */
export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  const box = size === 'sm' ? 'size-6 text-[11px]' : 'size-8 text-xs';

  return (
    <span
      aria-hidden
      className={`flex shrink-0 items-center justify-center rounded-full bg-raised font-semibold text-ink-soft ${box}`}
    >
      {name.trim().slice(0, 1).toUpperCase()}
    </span>
  );
}
