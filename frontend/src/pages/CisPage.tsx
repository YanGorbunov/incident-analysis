import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { del, get, post } from '../api';
import { useCanEdit } from '../auth';
import { Card, ErrorBox, IconButton, Icons, PageHeader, Pager, errorText, useReference, useToast } from '../components/ui';
import type { CiListItem, ImportResult, Page } from '../types';

/** Раздел «Конфигурационные единицы» (Рис. 3.9). */
export function CisPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const canEdit = useCanEdit();
  const ref = useReference();

  const [q, setQ] = useState('');
  const [ciTypeId, setCiTypeId] = useState('');
  const [institutionId, setInstitutionId] = useState('');
  const [sort, setSort] = useState('code');
  const [desc, setDesc] = useState(false);
  const [page, setPage] = useState(0);
  const [importId, setImportId] = useState('');
  const size = 20;

  const params = new URLSearchParams({ sort, desc: String(desc), page: String(page), size: String(size) });
  if (q) params.set('q', q);
  if (ciTypeId) params.set('ciTypeId', ciTypeId);
  if (institutionId) params.set('institutionId', institutionId);

  const list = useQuery({
    queryKey: ['cis', params.toString()],
    queryFn: () => get<Page<CiListItem>>(`/cis?${params}`),
    placeholderData: keepPreviousData,
  });

  const importMutation = useMutation({
    mutationFn: (id: string) => post<ImportResult>(`/import/cis/${encodeURIComponent(id)}`),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['cis'] });
      qc.invalidateQueries({ queryKey: ['reference'] });
      toast(r.outcome === 'CREATED' ? `КЕ ${r.code} импортирована` : `КЕ ${r.code} обновлена`);
      navigate(`/cis/${r.entityId}`);
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  const remove = useMutation({
    mutationFn: (id: number) => del(`/cis/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cis'] });
      toast('КЕ удалена');
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  const th = (key: string, title: string) => (
    <th
      className="sortable"
      onClick={() => {
        if (sort === key) setDesc(!desc);
        else {
          setSort(key);
          setDesc(false);
        }
      }}
    >
      {title}
      {sort === key && <span className="sort-mark">{desc ? ' ▼' : ' ▲'}</span>}
    </th>
  );

  return (
    <>
      <PageHeader title="Конфигурационные единицы">
        {canEdit && (
          <IconButton title="Добавить" onClick={() => navigate('/cis/new')}>
            {Icons.plus}
          </IconButton>
        )}
      </PageHeader>
      {canEdit && (
        <Card className="import-bar">
          <form
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              if (importId.trim()) importMutation.mutate(importId.trim());
            }}
          >
            <label htmlFor="ci-import">ID:</label>
            <input
              id="ci-import"
              placeholder="идентификатор КЕ в системе управления задачами, например ci-9001"
              value={importId}
              onChange={(e) => setImportId(e.target.value)}
            />
            <button className="primary" disabled={!importId.trim() || importMutation.isPending}>
              Импортировать
            </button>
          </form>
        </Card>
      )}
      <Card>
        <div className="toolbar">
          <select value={ciTypeId} onChange={(e) => { setCiTypeId(e.target.value); setPage(0); }}>
            <option value="">Все типы</option>
            {ref.data?.ciTypes.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <select value={institutionId} onChange={(e) => { setInstitutionId(e.target.value); setPage(0); }}>
            <option value="">Все ведомства</option>
            {ref.data?.institutions.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name}
              </option>
            ))}
          </select>
          <input className="search" type="search" placeholder="Поиск: имя, код, IP" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        <ErrorBox error={list.error} />
        <div className="table-wrap">
          <table className="grid">
            <thead>
              <tr>
                {th('code', 'Идентификатор')}
                {th('name', 'Наименование')}
                {th('type', 'Тип')}
                <th>IP-адрес</th>
                {th('institution', 'Ведомство')}
                {th('incidents', 'Инцидентов')}
                {canEdit && <th className="actions-col">Действия</th>}
              </tr>
            </thead>
            <tbody>
              {list.data?.items.map((c) => (
                <tr key={c.id}>
                  <td>
                    <Link to={`/cis/${c.id}`}>{c.code}</Link>
                  </td>
                  <td>{c.name}</td>
                  <td>{c.ciTypeName}</td>
                  <td className="mono">{c.ipAddress ?? '—'}</td>
                  <td>{c.institutionName}</td>
                  <td className="num">{c.incidentCount}</td>
                  {canEdit && (
                    <td className="actions-col">
                      <IconButton title="Редактировать" onClick={() => navigate(`/cis/${c.id}`)}>
                        {Icons.edit}
                      </IconButton>
                      <IconButton
                        title="Удалить"
                        onClick={() => confirm(`Удалить КЕ «${c.name}»?`) && remove.mutate(c.id)}
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
        {list.data && <Pager page={page} size={size} total={list.data.total} onPage={setPage} />}
      </Card>
    </>
  );
}
