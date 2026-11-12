import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { del, get, post } from '../api';
import { useCanEdit } from '../auth';
import { fmtDateTime } from '../format';
import { Card, ErrorBox, IconButton, Icons, PageHeader, Spinner, errorText, useReference, useToast } from '../components/ui';
import type { Category, IncidentCriteria, ReferenceBundle } from '../types';

/** Человекочитаемое описание сохранённого фильтра. */
export function describeCriteria(c: IncidentCriteria, r?: ReferenceBundle): string {
  const parts: string[] = [];
  const names = <T extends { id: number }>(ids: number[] | undefined, list: T[] | undefined, label: (x: T) => string) =>
    ids?.map((id) => list?.find((x) => x.id === id)).filter(Boolean).map((x) => label(x!)).join(', ');
  if (c.registeredFrom || c.registeredTo) {
    parts.push(`период: ${c.registeredFrom ? fmtDateTime(c.registeredFrom) : '…'} – ${c.registeredTo ? fmtDateTime(c.registeredTo) : '…'}`);
  }
  if (c.priorityIds?.length) parts.push(`приоритет: ${names(c.priorityIds, r?.priorities, (p) => p.name)}`);
  if (c.statusIds?.length) parts.push(`статус: ${names(c.statusIds, r?.statuses, (s) => s.name)}`);
  if (c.assigneeIds?.length) parts.push(`ответственный: ${names(c.assigneeIds, r?.employees, (e) => e.shortName)}`);
  if (c.unassigned) parts.push('неназначенные');
  if (c.institutionIds?.length) parts.push(`ведомство: ${names(c.institutionIds, r?.institutions, (i) => i.name)}`);
  if (c.ciIds?.length) parts.push(`КЕ: ${c.ciIds.length} шт.`);
  if (c.q) parts.push(`текст: «${c.q}»`);
  if (c.slaBreached != null) parts.push(c.slaBreached ? 'с нарушением SLA' : 'без нарушения SLA');
  return parts.length ? parts.join('; ') : 'все инциденты';
}

/** Раздел «Категории инцидентов» (Рис. 3.11). */
export function CategoriesPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const canEdit = useCanEdit();
  const ref = useReference();
  const list = useQuery({ queryKey: ['categories'], queryFn: () => get<Category[]>('/categories') });

  const refresh = useMutation({
    mutationFn: (id: number) => post<Category>(`/categories/${id}/refresh`),
    onSuccess: (c) => {
      qc.invalidateQueries({ queryKey: ['categories'] });
      toast(`Срез «${c.name}» обновлён: ${c.incidentCount} инц.`);
    },
    onError: (e) => toast(errorText(e), 'error'),
  });
  const remove = useMutation({
    mutationFn: (id: number) => del(`/categories/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
    onError: (e) => toast(errorText(e), 'error'),
  });

  return (
    <>
      <PageHeader title="Категории инцидентов">
        {canEdit && (
          <IconButton title="Создать категорию" onClick={() => navigate('/categories/new')}>
            {Icons.plus}
          </IconButton>
        )}
      </PageHeader>
      <Card>
        <p className="muted">
          Категория — сохранённый фильтр и зафиксированный по нему срез инцидентов. Отчёт строится по срезу, поэтому не
          меняется, пока срез не обновят.
        </p>
        <ErrorBox error={list.error} />
        {list.isLoading && <Spinner />}
        <div className="table-wrap">
          <table className="grid">
            <thead>
              <tr>
                <th>Код</th>
                <th>Наименование</th>
                <th>Параметры</th>
                <th>Инцидентов</th>
                <th>Срез на</th>
                <th className="actions-col">Действия</th>
              </tr>
            </thead>
            <tbody>
              {list.data?.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty">
                    Категорий пока нет
                  </td>
                </tr>
              )}
              {list.data?.map((c) => (
                <tr key={c.id}>
                  <td className="mono">{c.code}</td>
                  <td>
                    <Link to={`/categories/${c.id}`}>{c.name}</Link>
                  </td>
                  <td className="small">{describeCriteria(c.criteria, ref.data)}</td>
                  <td className="num">{c.incidentCount}</td>
                  <td>{fmtDateTime(c.snapshotAt)}</td>
                  <td className="actions-col">
                    <IconButton title="Отчёт" onClick={() => navigate(`/reports?category=${c.id}`)}>
                      {Icons.report}
                    </IconButton>
                    {canEdit && (
                      <>
                        <IconButton title="Обновить срез" onClick={() => refresh.mutate(c.id)}>
                          {Icons.refresh}
                        </IconButton>
                        <IconButton title="Удалить" onClick={() => confirm(`Удалить категорию «${c.name}»?`) && remove.mutate(c.id)}>
                          {Icons.trash}
                        </IconButton>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
