import { useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Icon } from './Icon';

/** input·textarea·select 가 같은 테두리와 높이를 쓰도록 한 곳에 모아 둔다. */
export const controlClass =
  'w-full rounded-md border border-line bg-surface text-[13px] text-ink outline-none transition-colors placeholder:text-ink-faint hover:border-line-strong focus:border-accent focus:outline-none disabled:opacity-50';

function Label({ htmlFor, children }: { htmlFor: string; children: ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="text-xs font-medium text-ink-soft">
      {children}
    </label>
  );
}

function Hint({ children }: { children: ReactNode }) {
  return <p className="text-xs text-ink-faint">{children}</p>;
}

function Error({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={id} role="alert" className="text-xs text-danger">
      {children}
    </p>
  );
}

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
}

/** label + input + 힌트/에러. 접근성을 위해 label 과 input 을 id 로 연결한다. */
export function FormField({ label, hint, error, id, className = '', ...props }: FormFieldProps) {
  // name 을 id 로 쓰면 한 화면에 모달이 둘 이상일 때 id 가 겹쳐 label 이 엉뚱한 input 을 가리킨다.
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = error ? `${inputId}-error` : undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={inputId}>{label}</Label>
      <input
        {...props}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        className={`h-9 px-2.5 ${controlClass} ${error ? 'border-danger' : ''} ${className}`}
      />
      {hint && !error && <Hint>{hint}</Hint>}
      {error && <Error id={errorId!}>{error}</Error>}
    </div>
  );
}

interface TextareaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string;
}

/** 이슈 설명·댓글에서 쓰던 raw textarea 를 하나로 합쳤다. */
export function TextareaField({ label, error, id, rows = 4, className = '', ...props }: TextareaFieldProps) {
  const generatedId = useId();
  const textareaId = id ?? generatedId;
  const errorId = error ? `${textareaId}-error` : undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={textareaId}>{label}</Label>
      <textarea
        {...props}
        id={textareaId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        className={`resize-y px-2.5 py-2 leading-relaxed ${controlClass} ${className}`}
      />
      {error && <Error id={errorId!}>{error}</Error>}
    </div>
  );
}

/** 폼 전체에 대한 서버 에러 (예: 비밀번호 불일치, 중복 이메일) */
export function FormError({ children }: { children: ReactNode }) {
  return (
    <p
      role="alert"
      className="flex items-start gap-2 rounded-md border border-danger/25 bg-danger-soft px-2.5 py-2 text-[13px] text-danger"
    >
      <Icon name="alert" className="mt-px size-4" />
      <span className="min-w-0">{children}</span>
    </p>
  );
}
