import { useEffect, useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { del, post } from '../api';
import { useCanEdit } from '../auth';
import { CriteriaForm, cleanCriteria } from '../components/CriteriaForm';
import { DEFAULT_FIELDS, FieldPicker, IncidentTable } from '../components/IncidentTable';
import { Card, ErrorBox, IconButton, Icons, PageHeader, Pager, errorText, useToast } from '../components/ui';
import type { ImportResult, IncidentCriteria, IncidentListItem, Page, ReportField } from '../types';

function usePersistent<T>(key: string, initial: T): [T, (v: T) => void] {
  const [value, setValue] = useState<T>(() => {
    try {
      const raw = localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : initial;
    } catch {
      return initial;
    }
  });
  const set = (v: T) => {
    setValue(v);
    try {
      localStorage.setItem(key, JSON.stringify(v));
    } catch {
      /* приватный режим — не страшно */
    }
  };
  return [value, set];
}

/** Раздел «Инциденты» (Рис. 3.6). */
export function IncidentsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const canEdit = useCanEdit();

  const [fields, setFields] = usePersistent<ReportField[]>('incidents.fields', DEFAULT_FIELDS);
  const [size, setSize] = usePersistent('incidents.size', 20);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('registeredAt');
  const [desc, setDesc] = useState(true);
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [criteria, setCriteria] = useState<IncidentCriteria>({});
  const [importId, setImportId] = useState('');

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(q);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [q]);

  const effective = cleanCriteria({ ...criteria, q: debouncedQ || undefined });
  const list = useQuery({
    queryKey: ['incidents', effective, sort, desc, page, size],
    queryFn: () =>
      post<Page<IncidentListItem>>('/incidents/search', { criteria: effective, sort, desc, page, size }),
    placeholderData: keepPreviousData,
  });

  const importMutation = useMutation({
    mutationFn: (id: string) => post<ImportResult>(`/import/incidents/${encodeURIComponent(id)}`),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['incidents'] });
      toast(r.outcome === 'CREATED' ? `Инцидент ${r.code} импортирован` : `Инцидент ${r.code} обновлён из трекера`);
      r.warnings.forEach((w) => toast(w, 'warn'));
      navigate(`/incidents/${r.entityId}`);
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  const remove = useMutation({
    mutationFn: (id: number) => del(`/incidents/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['incidents'] });
      toast('Инцидент удалён');
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  const onImport = (e: FormEvent) => {
    e.preventDefault();
    if (importId.trim()) importMutation.mutate(importId.trim());
  };

  const onSort = (key: string) => {
    if (key === sort) setDesc(!desc);
    else {
      setSort(key);
      setDesc(false);
    }
    setPage(0);
  };

  return (
    <>
      <PageHeader title="Инциденты">
        {canEdit && (
          <IconButton title="Добавить вручную" onClick={() => navigate('/incidents/new')}>
            {Icons.plus}
          </IconButton>
        )}
      </PageHeader>

      {canEdit && (
        <Card className="import-bar">
          <form onSubmit={onImport}>
            <label htmlFor="import-id">ID:</label>
            <input
              id="import-id"
              placeholder="идентификатор задачи в системе управления задачами, например 131222846"
              value={importId}
              onChange={(e) => setImportId(e.target.value)}
            />
            <button className="primary" disabled={!importId.trim() || importMutation.isPending}>
              {importMutation.isPending ? 'Импорт…' : 'Импортировать'}
            </button>
          </form>
        </Card>
      )}

      <Card>
        <div className="toolbar">
          <FieldPicker value={fields} onChange={setFields} />
          <button type="button" className={showFilters ? 'toggle on' : 'toggle'} onClick={() => setShowFilters(!showFilters)}>
            Фильтры{Object.keys(cleanCriteria(criteria)).length ? ` (${Object.keys(cleanCriteria(criteria)).length})` : ''}
          </button>
          <label className="inline">
            Показывать:
            <select
              value={size}
              onChange={(e) => {
                setSize(Number(e.target.value));
                setPage(0);
              }}
            >
              {[8, 20, 50, 100].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <input className="search" type="search" placeholder="Поиск" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>

        {showFilters && (
          <div className="filters">
            <CriteriaForm
              value={criteria}
              onChange={(c) => {
                setCriteria(c);
                setPage(0);
              }}
            />
            <button type="button" className="link" onClick={() => setCriteria({})}>
              Сбросить фильтры
            </button>
          </div>
        )}

        <ErrorBox error={list.error} />
        <IncidentTable
          items={list.data?.items ?? []}
          fields={fields}
          sort={sort}
          desc={desc}
          onSort={onSort}
          actions={
            canEdit
              ? (i) => (
                  <>
                    <IconButton title="Редактировать" onClick={() => navigate(`/incidents/${i.id}`)}>
                      {Icons.edit}
                    </IconButton>
                    <IconButton
                      title="Удалить"
                      onClick={() => {
                        if (confirm(`Удалить инцидент ${i.code} «${i.title}»?`)) remove.mutate(i.id);
                      }}
                    >
                      {Icons.trash}
                    </IconButton>
                  </>
                )
              : undefined
          }
        />
        {list.data && <Pager page={page} size={size} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
