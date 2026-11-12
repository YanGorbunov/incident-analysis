import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { get } from '../api';
import type { ReferenceBundle } from '../types';

// ---------------------------------------------------------------- справочники
export function useReference() {
  return useQuery({
    queryKey: ['reference'],
    queryFn: () => get<ReferenceBundle>('/reference'),
    staleTime: 5 * 60_000,
  });
}

// ---------------------------------------------------------------- уведомления
type Toast = { id: number; text: string; kind: 'ok' | 'error' | 'warn' };
const ToastContext = createContext<(text: string, kind?: Toast['kind']) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((text: string, kind: Toast['kind'] = 'ok') => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, text, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), kind === 'ok' ? 3500 : 8000);
  }, []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.kind}`}>
            {t.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);

/** Сообщение ошибки для показа пользователю. */
export function errorText(e: unknown): string {
  return e instanceof Error ? e.message : 'Неизвестная ошибка';
}

// ---------------------------------------------------------------- разметка
export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <header className="page-header">
      <h1>{title}</h1>
      {children}
    </header>
  );
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="error-box">{errorText(error)}</div>;
}

export function Spinner() {
  return <div className="spinner" aria-label="Загрузка" />;
}

export function IconButton({
  title,
  onClick,
  children,
  disabled,
}: {
  title: string;
  onClick: () => void;
  children: ReactNode;
  disabled?: boolean;
}) {
  return (
    <button type="button" className="icon-btn" title={title} aria-label={title} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

export const Icons = {
  plus: (
    <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 7v10M7 12h10" />
    </svg>
  ),
  edit: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
      <path d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25zm17.7-10.2a1 1 0 0 0 0-1.42l-2.33-2.33a1 1 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.83-1.83z" />
    </svg>
  ),
  trash: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
      <path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
    </svg>
  ),
  save: (
    <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor">
      <path d="M17 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7l-4-4zm-5 16a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm3-10H5V5h10v4z" />
    </svg>
  ),
  refresh: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
      <path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z" />
    </svg>
  ),
  report: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
      <path d="M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z" />
    </svg>
  ),
  user: (
    <svg viewBox="0 0 24 24" width="40" height="40" fill="currentColor">
      <path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4 0-9 2-9 5v2h18v-2c0-3-5-5-9-5z" />
    </svg>
  ),
  logout: (
    <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" strokeWidth="1.6">
      <path d="M14 4H5v16h9M10 12h11M17 8l4 4-4 4" />
    </svg>
  ),
};

// ---------------------------------------------------------------- пагинация
export function Pager({
  page,
  size,
  total,
  onPage,
}: {
  page: number;
  size: number;
  total: number;
  onPage: (p: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <div className="pager">
      <span className="muted">
        Элементы: {from} – {to} из {total}
      </span>
      <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>
        ‹
      </button>
      <span>
        {page + 1} / {pages}
      </span>
      <button type="button" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
        ›
      </button>
    </div>
  );
}

// ---------------------------------------------------------------- модальное окно
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="icon-btn" aria-label="Закрыть" onClick={onClose}>
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- список флажков
export function CheckList<T extends { id: number }>({
  items,
  selected,
  onChange,
  label,
}: {
  items: T[];
  selected: number[] | undefined;
  onChange: (ids: number[] | undefined) => void;
  label: (item: T) => ReactNode;
}) {
  const sel = selected ?? [];
  const toggle = (id: number) => {
    const next = sel.includes(id) ? sel.filter((x) => x !== id) : [...sel, id];
    onChange(next.length ? next : undefined);
  };
  return (
    <div className="checklist">
      {items.map((it) => (
        <label key={it.id}>
          <input type="checkbox" checked={sel.includes(it.id)} onChange={() => toggle(it.id)} />
          {label(it)}
        </label>
      ))}
    </div>
  );
}

/** Множественный выбор из длинного списка (ответственные, КЕ). */
export function MultiSelect<T extends { id: number }>({
  items,
  selected,
  onChange,
  label,
  placeholder = 'Все',
}: {
  items: T[];
  selected: number[] | undefined;
  onChange: (ids: number[] | undefined) => void;
  label: (item: T) => string;
  placeholder?: string;
}) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const sel = selected ?? [];
  const chosen = items.filter((i) => sel.includes(i.id));
  const visible = items.filter((i) => label(i).toLowerCase().includes(filter.toLowerCase()));
  return (
    <div className="multiselect">
      <button type="button" className="multiselect-btn" onClick={() => setOpen(!open)}>
        {chosen.length ? chosen.map(label).join(', ') : placeholder}
        <span className="caret">▾</span>
      </button>
      {open && (
        <div className="multiselect-pop" onMouseLeave={() => setOpen(false)}>
          <input autoFocus placeholder="Поиск" value={filter} onChange={(e) => setFilter(e.target.value)} />
          <CheckList items={visible} selected={selected} onChange={onChange} label={label} />
          {sel.length > 0 && (
            <button type="button" className="link" onClick={() => onChange(undefined)}>
              Сбросить
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export function PriorityBadge({ name, color }: { name: string; color: string }) {
  return (
    <span className="badge">
      <span className="dot" style={{ background: color }} />
      {name}
    </span>
  );
}
