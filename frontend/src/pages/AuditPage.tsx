import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { get } from '../api';
import { fmtDateTime } from '../format';
import { Card, ErrorBox, PageHeader, Pager } from '../components/ui';
import type { AuditEvent, ImportRun, Page } from '../types';

const OUTCOME: Record<ImportRun['outcome'], string> = {
  CREATED: 'создан',
  UPDATED: 'обновлён',
  NOT_FOUND: 'не найден',
  FAILED: 'ошибка',
};

/** Журнал событий (п. 3.4 ВКР) и журнал импортов. */
export function AuditPage() {
  const [tab, setTab] = useState<'audit' | 'imports'>('audit');
  return (
    <>
      <PageHeader title="Журнал событий" />
      <div className="tabs">
        <button type="button" className={tab === 'audit' ? 'active' : ''} onClick={() => setTab('audit')}>
          Действия пользователей
        </button>
        <button type="button" className={tab === 'imports' ? 'active' : ''} onClick={() => setTab('imports')}>
          Импорт из трекера
        </button>
      </div>
      {tab === 'audit' ? <AuditTable /> : <ImportTable />}
    </>
  );
}

function AuditTable() {
  const [username, setUsername] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const size = 50;
  const actions = useQuery({ queryKey: ['audit-actions'], queryFn: () => get<string[]>('/admin/audit/actions') });
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (username) params.set('username', username);
  if (action) params.set('action', action);
  const events = useQuery({
    queryKey: ['audit', params.toString()],
    queryFn: () => get<Page<AuditEvent>>(`/admin/audit?${params}`),
    placeholderData: keepPreviousData,
  });

  return (
    <Card>
      <div className="toolbar">
        <input placeholder="Пользователь" value={username} onChange={(e) => { setUsername(e.target.value); setPage(0); }} />
        <select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
          <option value="">Все действия</option>
          {actions.data?.map((a) => (
            <option key={a}>{a}</option>
          ))}
        </select>
      </div>
      <ErrorBox error={events.error} />
      <div className="table-wrap">
        <table className="grid compact">
          <thead>
            <tr>
              <th>Когда</th>
              <th>Пользователь</th>
              <th>Действие</th>
              <th>Объект</th>
              <th>Подробности</th>
              <th>Адрес</th>
            </tr>
          </thead>
          <tbody>
            {events.data?.items.map((e) => (
              <tr key={e.id} className={e.action.includes('FAILURE') || e.action.includes('LOCKED') ? 'row-warn' : undefined}>
                <td>{fmtDateTime(e.occurredAt)}</td>
                <td className="mono">{e.username ?? '—'}</td>
                <td className="mono">{e.action}</td>
                <td>{e.entityType ? `${e.entityType} ${e.entityId ?? ''}` : '—'}</td>
                <td className="mono small">{e.details ?? ''}</td>
                <td className="mono small">{e.remoteAddr ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {events.data && <Pager page={page} size={size} total={events.data.total} onPage={setPage} />}
    </Card>
  );
}

function ImportTable() {
  const runs = useQuery({ queryKey: ['import-runs'], queryFn: () => get<ImportRun[]>('/import/runs') });
  return (
    <Card>
      <ErrorBox error={runs.error} />
      <div className="table-wrap">
        <table className="grid compact">
          <thead>
            <tr>
              <th>Когда</th>
              <th>Что</th>
              <th>ID в трекере</th>
              <th>Результат</th>
              <th>Сообщение</th>
              <th>Кто</th>
              <th>мс</th>
            </tr>
          </thead>
          <tbody>
            {runs.data?.map((r) => (
              <tr key={r.id} className={r.outcome === 'FAILED' ? 'row-warn' : undefined}>
                <td>{fmtDateTime(r.startedAt)}</td>
                <td>{r.kind === 'INCIDENT' ? 'инцидент' : 'КЕ'}</td>
                <td className="mono">{r.externalId}</td>
                <td>{OUTCOME[r.outcome]}</td>
                <td className="small">{r.message || '—'}</td>
                <td className="mono">{r.requestedBy}</td>
                <td className="num">{r.durationMs}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
