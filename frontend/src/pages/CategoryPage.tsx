import { useEffect, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router';
import { get, post, put } from '../api';
import { useCanEdit } from '../auth';
import { CriteriaForm, cleanCriteria } from '../components/CriteriaForm';
import { IncidentTable } from '../components/IncidentTable';
import { Card, ErrorBox, PageHeader, Pager, Spinner, errorText, useToast } from '../components/ui';
import type { Category, IncidentCriteria, IncidentListItem, Page, ReportField } from '../types';

const PREVIEW_FIELDS: ReportField[] = ['CODE', 'TITLE', 'REGISTERED_AT', 'CLOSED_AT', 'PRIORITY', 'STATUS', 'ASSIGNEE'];

/** Экранная форма «Категория инцидентов» (Рис. 3.12): параметры → «Применить» → «Сохранить». */
export function CategoryPage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const canEdit = useCanEdit();

  const existing = useQuery({
    queryKey: ['category', id],
    queryFn: () => get<Category>(`/categories/${id}`),
    enabled: !isNew,
  });

  const [criteria, setCriteria] = useState<IncidentCriteria>({});
  const [applied, setApplied] = useState<IncidentCriteria>({});
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (existing.data) {
      setCriteria(existing.data.criteria);
      setApplied(existing.data.criteria);
      setName(existing.data.name);
      setDescription(existing.data.description ?? '');
    }
  }, [existing.data]);

  const preview = useQuery({
    queryKey: ['incidents', 'preview', applied, page],
    queryFn: () =>
      post<Page<IncidentListItem>>('/incidents/search', {
        criteria: cleanCriteria(applied),
        sort: 'registeredAt',
        desc: true,
        page,
        size: 10,
      }),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: () => {
      const body = { name, description: description || null, criteria: cleanCriteria(criteria) };
      return isNew ? post<{ id: number }>('/categories', body) : put<void>(`/categories/${id}`, body);
    },
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['categories'] });
      qc.invalidateQueries({ queryKey: ['category', id] });
      toast('Категория сохранена, срез зафиксирован');
      if (isNew && res) navigate(`/categories/${(res as { id: number }).id}`, { replace: true });
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  if (!isNew && existing.isLoading) return <Spinner />;
  if (existing.error) return <ErrorBox error={existing.error} />;

  const dirty = JSON.stringify(cleanCriteria(criteria)) !== JSON.stringify(cleanCriteria(applied));

  return (
    <>
      <PageHeader title={isNew ? 'Новая категория инцидентов' : 'Категория инцидентов'} />
      <Card className="filters">
        <CriteriaForm value={criteria} onChange={setCriteria} />
      </Card>
      <Card>
        <ErrorBox error={preview.error} />
        <IncidentTable items={preview.data?.items ?? []} fields={PREVIEW_FIELDS} />
        {preview.data && <Pager page={page} size={10} total={preview.data.total} onPage={setPage} />}
      </Card>
      <Card className="save-bar">
        <button
          type="button"
          className="primary"
          onClick={() => {
            setApplied(criteria);
            setPage(0);
          }}
        >
          Применить{dirty ? ' *' : ''}
        </button>
        <label className="inline grow">
          Наименование:
          <input value={name} placeholder="Например: все за декабрь 2023 – февраль 2024" onChange={(e) => setName(e.target.value)} />
        </label>
        <input
          className="grow"
          placeholder="Описание (необязательно)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        {canEdit && (
          <button
            type="button"
            className="primary"
            disabled={!name.trim() || save.isPending || dirty}
            title={dirty ? 'Сначала примените параметры' : undefined}
            onClick={() => save.mutate()}
          >
            Сохранить
          </button>
        )}
      </Card>
    </>
  );
}
