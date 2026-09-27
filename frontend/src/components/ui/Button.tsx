import type { ButtonHTMLAttributes } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'dangerSolid';
  size?: 'sm' | 'md' | 'icon';
}

/**
 * 주 버튼은 먹색이다. 강조색(파랑)은 링크와 활성 표시에만 쓴다 —
 * 화면마다 파란 버튼이 있으면 무엇이 중요한지 구별되지 않는다.
 */
const VARIANT = {
  primary: 'bg-ink text-canvas hover:opacity-90',
  secondary: 'border border-line bg-surface text-ink hover:bg-raised',
  ghost: 'text-ink-soft hover:bg-raised hover:text-ink',
  danger: 'text-danger hover:bg-danger-soft',
  dangerSolid: 'bg-danger text-white hover:opacity-90',
} as const;

const SIZE = {
  sm: 'h-7 gap-1 px-2.5 text-[13px]',
  md: 'h-9 gap-1.5 px-3.5 text-[13px]',
  icon: 'size-8',
} as const;

export function Button({ variant = 'primary', size = 'md', className = '', children, ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center rounded-md font-medium whitespace-nowrap transition-[background-color,opacity,border-color] disabled:pointer-events-none disabled:opacity-45 ${VARIANT[variant]} ${SIZE[size]} ${className}`}
    >
      {children}
    </button>
  );
}
