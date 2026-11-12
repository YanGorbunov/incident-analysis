import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { fmtDateTime, fmtDuration } from '../format';
import type { IncidentListItem, ReportField } from '../types';
import { PriorityBadge } from './ui';

export interface Column {
  key: ReportField;
  title: string;
  /** Ключ сортировки на бэкенде (если колонка сортируемая). */
  sort?: string;
  render: (i: IncidentListItem) => ReactNode;
}

export const COLUMNS: Column[] = [
  { key: 'CODE', title: 'Идентификатор', sort: 'code', render: (i) => <Link to={`/incidents/${i.id}`}>{i.code}</Link> },
  { key: 'TITLE', title: 'Наименование', sort: 'title', render: (i) => <span className="clamp">{i.title}</span> },
  { key: 'REGISTERED_AT', title: 'Регистрация', sort: 'registeredAt', render: (i) => fmtDateTime(i.registeredAt) },
  { key: 'CLOSED_AT', title: 'Закрытие', sort: 'closedAt', render: (i) => fmtDateTime(i.closedAt) },
  {
    key: 'PRIORITY',
    title: 'Приоритет',
    sort: 'priority',
    render: (i) => <PriorityBadge name={i.priorityName} color={i.priorityColor} />,
  },
  { key: 'STATUS', title: 'Статус', sort: 'status', render: (i) => i.statusName },
  { key: 'ASSIGNEE', title: 'Ответственный', sort: 'assignee', render: (i) => i.assigneeName ?? '—' },
  { key: 'CIS', title: 'КЕ', render: (i) => <span className="clamp">{i.cis ?? '—'}</span> },
  { key: 'INSTITUTIONS', title: 'Ведомства', render: (i) => <span className="clamp">{i.institutions ?? '—'}</span> },
  { key: 'RESOLUTION_TIME', title: 'Время решения', sort: 'resolution', render: (i) => fmtDuration(i.resolutionSeconds) },
  {
    key: 'SLA',
    title: 'SLA',
    render: (i) => (i.slaBreached ? <span className="sla-bad">нарушен</span> : <span className="sla-ok">в норме</span>),
  },
];

/** Поля по умолчанию — как на макете раздела «Инциденты» (Рис. 3.6). */
export const DEFAULT_FIELDS: ReportField[] = ['TITLE', 'REGISTERED_AT', 'CLOSED_AT', 'PRIORITY', 'STATUS', 'ASSIGNEE'];

export function IncidentTable({
  items,
  fields,
  sort,
  desc,
  onSort,
  actions,
}: {
  items: IncidentListItem[];
  fields: ReportField[];
  sort?: string;
  desc?: boolean;
  onSort?: (key: string) => void;
  actions?: (i: IncidentListItem) => ReactNode;
}) {
  const cols = COLUMNS.filter((c) => fields.includes(c.key));
  return (
    <div className="table-wrap">
      <table className="grid">
        <thead>
          <tr>
            {cols.map((c) => (
              <th
                key={c.key}
                className={c.sort && onSort ? 'sortable' : undefined}
                onClick={c.sort && onSort ? () => onSort(c.sort!) : undefined}
              >
                {c.title}
                {c.sort && c.sort === sort && <span className="sort-mark">{desc ? ' ▼' : ' ▲'}</span>}
              </th>
            ))}
            {actions && <th className="actions-col">Действия</th>}
          </tr>
        </thead>
        <tbody>
          {items.length === 0 && (
            <tr>
              <td colSpan={cols.length + (actions ? 1 : 0)} className="empty">
                Инцидентов не найдено
              </td>
            </tr>
          )}
          {items.map((i) => (
            <tr key={i.id} className={i.slaBreached && i.statusPhase !== 'CLOSED' ? 'row-warn' : undefined}>
              {cols.map((c) => (
                <td key={c.key}>{c.render(i)}</td>
              ))}
              {actions && <td className="actions-col">{actions(i)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Выбор отображаемых колонок («Поля» на макетах). */
export function FieldPicker({ value, onChange }: { value: ReportField[]; onChange: (v: ReportField[]) => void }) {
  return (
    <details className="field-picker">
      <summary>Поля ({value.length})</summary>
      <div className="field-picker-pop">
        {COLUMNS.map((c) => (
          <label key={c.key}>
            <input
              type="checkbox"
              checked={value.includes(c.key)}
              onChange={() =>
                onChange(
                  value.includes(c.key)
                    ? value.filter((k) => k !== c.key)
                    : COLUMNS.map((x) => x.key).filter((k) => k === c.key || value.includes(k)),
                )
              }
            />
            {c.title}
          </label>
        ))}
      </div>
    </details>
  );
}
