import { useId, type SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
}

/** 필터 바와 폼에서 함께 쓴다. label 을 주지 않으면 aria-label 로 이름만 붙인다. */
export function Select({ label, id, className = '', children, ...props }: SelectProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={selectId} className="text-sm font-medium text-slate-700 dark:text-slate-300">
          {label}
        </label>
      )}
      <select
        {...props}
        id={selectId}
        className={`h-9 rounded-md border border-slate-300 bg-white px-2 text-sm outline-none focus:border-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-slate-400 ${className}`}
      >
        {children}
      </select>
    </div>
  );
}
