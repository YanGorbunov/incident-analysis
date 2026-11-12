import { useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { del, post, put } from '../api';
import { useAuth } from '../auth';
import { fmtMinutes } from '../format';
import { Card, IconButton, Icons, Modal, PageHeader, PriorityBadge, Spinner, errorText, useReference, useToast } from '../components/ui';
import type { ReferenceBundle } from '../types';

type Row = { id: number; code: string } & Record<string, unknown>;

interface FieldDef {
  key: string;
  label: string;
  type: 'text' | 'textarea' | 'number' | 'color' | 'select' | 'checkbox';
  required?: boolean;
  options?: (b: ReferenceBundle) => { value: string | number; label: string }[];
  /** Поле можно задать только при создании (например, отдел должности — он в её коде). */
  createOnly?: boolean;
  hint?: string;
}

interface TabDef {
  key: string;
  title: string;
  endpoint: string;
  adminOnly: boolean;
  rows: (b: ReferenceBundle) => Row[];
  columns: { title: string; render: (r: Row) => ReactNode }[];
  fields: FieldDef[];
  defaults?: Record<string, unknown>;
}

const PHASES = [
  { value: 'NEW', label: 'Новый (ещё не взят в работу)' },
  { value: 'ACTIVE', label: 'В работе (есть реакция)' },
  { value: 'RESOLVED', label: 'Решён (ждёт подтверждения)' },
  { value: 'CLOSED', label: 'Закрыт' },
];

const s = (v: unknown) => (v == null || v === '' ? '—' : String(v));

const TABS: TabDef[] = [
  {
    key: 'statuses',
    title: 'Статусы',
    endpoint: 'statuses',
    adminOnly: true,
    rows: (b) => b.statuses as unknown as Row[],
    columns: [
      { title: 'Наименование', render: (r) => s(r.name) },
      { title: 'Описание', render: (r) => s(r.description) },
      { title: 'Фаза', render: (r) => PHASES.find((p) => p.value === r.phase)?.label ?? s(r.phase) },
    ],
    fields: [
      { key: 'name', label: 'Наименование', type: 'text', required: true },
      { key: 'description', label: 'Описание', type: 'textarea' },
      { key: 'sortOrder', label: 'Порядок', type: 'number' },
      {
        key: 'phase',
        label: 'Фаза',
        type: 'select',
        required: true,
        options: () => PHASES,
        hint: 'По фазе считаются закрытые инциденты, время реакции и SLA',
      },
    ],
    defaults: { sortOrder: 10, phase: 'NEW' },
  },
  {
    key: 'priorities',
    title: 'Приоритеты',
    endpoint: 'priorities',
    adminOnly: true,
    rows: (b) => b.priorities as unknown as Row[],
    columns: [
      { title: 'Наименование', render: (r) => <PriorityBadge name={String(r.name)} color={String(r.color)} /> },
      { title: 'Описание', render: (r) => <span className="clamp">{s(r.description)}</span> },
      { title: 'SLA реакции', render: (r) => fmtMinutes(r.responseSlaMinutes as number | undefined) },
      { title: 'SLA решения', render: (r) => fmtMinutes(r.resolutionSlaMinutes as number | undefined) },
    ],
    fields: [
      { key: 'name', label: 'Наименование', type: 'text', required: true },
      { key: 'description', label: 'Описание', type: 'textarea' },
      { key: 'sortOrder', label: 'Порядок', type: 'number' },
      { key: 'color', label: 'Цвет на диаграммах', type: 'color', required: true },
      { key: 'responseSlaMinutes', label: 'SLA реакции, мин', type: 'number' },
      { key: 'resolutionSlaMinutes', label: 'SLA решения, мин', type: 'number', hint: 'Пусто — SLA не контролируется' },
    ],
    defaults: { sortOrder: 10, color: '#888888' },
  },
  {
    key: 'institutions',
    title: 'Ведомства',
    endpoint: 'institutions',
    adminOnly: false,
    rows: (b) => b.institutions as unknown as Row[],
    columns: [
      { title: 'Наименование', render: (r) => s(r.name) },
      { title: 'Телефон', render: (r) => s(r.phoneNumber) },
      { title: 'Адрес', render: (r) => s(r.address) },
    ],
    fields: [
      { key: 'name', label: 'Наименование', type: 'text', required: true },
      { key: 'phoneNumber', label: 'Телефон (+7…)', type: 'text' },
      { key: 'address', label: 'Адрес', type: 'text' },
    ],
  },
  {
    key: 'ci-types',
    title: 'Типы КЕ',
    endpoint: 'ci-types',
    adminOnly: false,
    rows: (b) => b.ciTypes as unknown as Row[],
    columns: [{ title: 'Наименование', render: (r) => s(r.name) }],
    fields: [{ key: 'name', label: 'Наименование', type: 'text', required: true }],
  },
  {
    key: 'software',
    title: 'ПО',
    endpoint: 'software',
    adminOnly: false,
    rows: (b) => b.software as unknown as Row[],
    columns: [
      { title: 'Наименование', render: (r) => s(r.name) },
      { title: 'Версия', render: (r) => s(r.version) },
      { title: 'Вендор', render: (r) => s(r.vendor) },
    ],
    fields: [
      { key: 'name', label: 'Наименование', type: 'text', required: true },
      { key: 'version', label: 'Версия', type: 'text' },
      { key: 'vendor', label: 'Вендор', type: 'text' },
      { key: 'licenseKey', label: 'Ключ лицензии', type: 'text' },
    ],
  },
  {
    key: 'employees',
    title: 'Сотрудники',
    endpoint: 'employees',
    adminOnly: false,
    rows: (b) => b.employees as unknown as Row[],
    columns: [
      { title: 'ФИО', render: (r) => `${r.lastName} ${r.firstName} ${r.middleName ?? ''}` },
      { title: 'Должность', render: (r) => s(r.postName) },
      { title: 'Отдел', render: (r) => s(r.departmentName) },
      { title: 'ID в трекере', render: (r) => s(r.externalId) },
      { title: 'Активен', render: (r) => (r.active ? 'да' : 'нет') },
    ],
    fields: [
      { key: 'lastName', label: 'Фамилия', type: 'text', required: true },
      { key: 'firstName', label: 'Имя', type: 'text', required: true },
      { key: 'middleName', label: 'Отчество', type: 'text' },
      { key: 'email', label: 'E-mail', type: 'text' },
      {
        key: 'postId',
        label: 'Должность',
        type: 'select',
        required: true,
        options: (b) => b.posts.map((p) => ({ value: p.id, label: `${p.code} ${p.name}` })),
        hint: 'Код сотрудника строится из кода должности и при её смене не меняется',
      },
      { key: 'externalId', label: 'ID в трекере', type: 'text', hint: 'Нужен, чтобы импорт нашёл ответственного' },
      { key: 'active', label: 'Активен', type: 'checkbox' },
    ],
    defaults: { active: true },
  },
  {
    key: 'departments',
    title: 'Отделы',
    endpoint: 'departments',
    adminOnly: true,
    rows: (b) => b.departments as unknown as Row[],
    columns: [
      { title: 'Наименование', render: (r) => s(r.name) },
      { title: 'Описание', render: (r) => <span className="clamp">{s(r.description)}</span> },
      { title: 'Расположение', render: (r) => s(r.location) },
    ],
    fields: [
      { key: 'name', label: 'Наименование', type: 'text', required: true },
      { key: 'description', label: 'Описание', type: 'textarea' },
      { key: 'location', label: 'Расположение', type: 'text' },
    ],
  },
  {
    key: 'posts',
    title: 'Должности',
    endpoint: 'posts',
    adminOnly: true,
    rows: (b) => b.posts as unknown as Row[],
    columns: [
      { title: 'Наименование', render: (r) => s(r.name) },
      { title: 'Отдел', render: (r) => s(r.departmentName) },
    ],
    fields: [
      {
        key: 'departmentId',
        label: 'Отдел',
        type: 'select',
        required: true,
        createOnly: true,
        options: (b) => b.departments.map((d) => ({ value: d.id, label: `${d.code} ${d.name}` })),
      },
      { key: 'name', label: 'Наименование', type: 'text', required: true },
      { key: 'description', label: 'Описание', type: 'textarea' },
    ],
  },
];

/** Раздел «Справочники» (Рис. 3.8): в ВКР — только статусы и приоритеты. */
export function ReferencesPage() {
  const ref = useReference();
  const { can } = useAuth();
  const [tabKey, setTabKey] = useState(TABS[0].key);
  const [editing, setEditing] = useState<Row | 'new' | null>(null);
  const qc = useQueryClient();
  const toast = useToast();

  const tab = TABS.find((t) => t.key === tabKey)!;
  const editable = tab.adminOnly ? can('ADMIN') : can('ADMIN', 'ANALYST');

  const remove = useMutation({
    mutationFn: (id: number) => del(`/reference/${tab.endpoint}/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reference'] });
      toast('Запись удалена');
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  if (!ref.data) return <Spinner />;
  const rows = tab.rows(ref.data);

  return (
    <>
      <PageHeader title="Справочники" />
      <div className="tabs">
        {TABS.map((t) => (
          <button key={t.key} type="button" className={t.key === tabKey ? 'active' : ''} onClick={() => setTabKey(t.key)}>
            {t.title}
          </button>
        ))}
      </div>
      <Card>
        <div className="card-head">
          <h2>{tab.title}</h2>
          {editable && (
            <IconButton title="Добавить" onClick={() => setEditing('new')}>
              {Icons.plus}
            </IconButton>
          )}
          {tab.adminOnly && !can('ADMIN') && <span className="muted">изменяет администратор</span>}
        </div>
        <div className="table-wrap">
          <table className="grid">
            <thead>
              <tr>
                <th>Код</th>
                {tab.columns.map((c) => (
                  <th key={c.title}>{c.title}</th>
                ))}
                {editable && <th className="actions-col">Действия</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="mono">{r.code}</td>
                  {tab.columns.map((c) => (
                    <td key={c.title}>{c.render(r)}</td>
                  ))}
                  {editable && (
                    <td className="actions-col">
                      <IconButton title="Редактировать" onClick={() => setEditing(r)}>
                        {Icons.edit}
                      </IconButton>
                      <IconButton
                        title="Удалить"
                        onClick={() => confirm('Удалить запись? Если она используется, удаление не пройдёт.') && remove.mutate(r.id)}
                      >
                        {Icons.trash}
                      </IconButton>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      {editing && (
        <RefEditor tab={tab} bundle={ref.data} row={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      )}
    </>
  );
}

function RefEditor({ tab, bundle, row, onClose }: { tab: TabDef; bundle: ReferenceBundle; row: Row | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [values, setValues] = useState<Record<string, unknown>>(() => {
    const init: Record<string, unknown> = { ...(tab.defaults ?? {}) };
    for (const f of tab.fields) if (row && row[f.key] !== undefined) init[f.key] = row[f.key];
    return init;
  });
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => {
      const body: Record<string, unknown> = {};
      for (const f of tab.fields) {
        const v = values[f.key];
        body[f.key] =
          f.type === 'number' || (f.type === 'select' && f.key.endsWith('Id'))
            ? v === '' || v == null
              ? null
              : Number(v)
            : f.type === 'checkbox'
              ? !!v
              : v === ''
                ? null
                : v;
      }
      return row ? put(`/reference/${tab.endpoint}/${row.id}`, body) : post(`/reference/${tab.endpoint}`, body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reference'] });
      toast('Сохранено');
      onClose();
    },
    onError: (e) => setError(errorText(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <Modal title={`${tab.title}: ${row ? 'изменение' : 'новая запись'}`} onClose={onClose}>
      <form className="modal-form" onSubmit={submit}>
        {tab.fields.map((f) => {
          const v = values[f.key];
          const setV = (nv: unknown) => setValues((x) => ({ ...x, [f.key]: nv }));
          const disabled = !!row && !!f.createOnly;
          let input: ReactNode;
          switch (f.type) {
            case 'textarea':
              input = <textarea rows={3} value={String(v ?? '')} onChange={(e) => setV(e.target.value)} />;
              break;
            case 'number':
              input = <input type="number" min={0} value={v == null ? '' : String(v)} onChange={(e) => setV(e.target.value)} />;
              break;
            case 'color':
              input = <input type="color" value={String(v ?? '#888888')} onChange={(e) => setV(e.target.value)} />;
              break;
            case 'checkbox':
              input = <input type="checkbox" checked={!!v} onChange={(e) => setV(e.target.checked)} />;
              break;
            case 'select':
              input = (
                <select required={f.required} disabled={disabled} value={v == null ? '' : String(v)} onChange={(e) => setV(e.target.value)}>
                  <option value="" disabled>
                    выберите
                  </option>
                  {f.options!(bundle).map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              );
              break;
            default:
              input = <input required={f.required} value={String(v ?? '')} onChange={(e) => setV(e.target.value)} />;
          }
          return (
            <label key={f.key} className={f.type === 'checkbox' ? 'check-inline' : undefined}>
              <span>
                {f.label}
                {f.required && ' *'}
              </span>
              {input}
              {f.hint && <small className="muted">{f.hint}</small>}
            </label>
          );
        })}
        {error && <div className="error-box">{error}</div>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>
            Отмена
          </button>
          <button className="primary" disabled={save.isPending}>
            Сохранить
          </button>
        </div>
      </form>
    </Modal>
  );
}
