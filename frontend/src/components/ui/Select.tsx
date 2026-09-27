import { useId, type SelectHTMLAttributes } from 'react';
import { Icon } from './Icon';
import { controlClass } from './FormField';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
}

/**
 * 필터 바와 폼에서 함께 쓴다. label 을 주지 않으면 aria-label 로 이름만 붙인다.
 * 브라우저 기본 화살표는 OS 마다 모양이 달라서 지우고 직접 그린다.
 */
export function Select({ label, id, className = '', children, ...props }: SelectProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      {label && (
        <label htmlFor={selectId} className="text-xs font-medium text-ink-soft">
          {label}
        </label>
      )}
      <div className="relative">
        <select
          {...props}
          id={selectId}
          className={`h-8 appearance-none py-0 pl-2.5 pr-7 ${controlClass} ${className}`}
        >
          {children}
        </select>
        <Icon
          name="chevronDown"
          className="pointer-events-none absolute right-2 top-1/2 size-3.5 -translate-y-1/2 text-ink-faint"
        />
      </div>
    </div>
  );
}
