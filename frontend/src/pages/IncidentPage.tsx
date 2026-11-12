import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError, get, post, put } from '../api';
import { useCanEdit } from '../auth';
import { fmtDateTime, fmtMinutes, fromLocalInput, toLocalInput } from '../format';
import { Card, ErrorBox, IconButton, Icons, PageHeader, Spinner, errorText, useReference, useToast } from '../components/ui';
import type { CiListItem, CiRef, IncidentDetails, IncidentRequest, Page } from '../types';

interface FormState {
  title: string;
  description: string;
  resolution: string;
  registeredAt: string;
  firstResponseAt: string;
  resolvedAt: string;
  closedAt: string;
  priorityId?: number;
  statusId?: number;
  assigneeId?: number;
  externalId: string;
  cis: CiRef[];
  version?: number;
}

const empty = (): FormState => ({
  title: '',
  description: '',
  resolution: '',
  registeredAt: toLocalInput(new Date().toISOString()),
  firstResponseAt: '',
  resolvedAt: '',
  closedAt: '',
  externalId: '',
  cis: [],
});

function fromDetails(d: IncidentDetails): FormState {
  return {
    title: d.title,
    description: d.description,
    resolution: d.resolution ?? '',
    registeredAt: toLocalInput(d.registeredAt),
    firstResponseAt: toLocalInput(d.firstResponseAt),
    resolvedAt: toLocalInput(d.resolvedAt),
    closedAt: toLocalInput(d.closedAt),
    priorityId: d.priorityId,
    statusId: d.statusId,
    assigneeId: d.assigneeId,
    externalId: d.externalId ?? '',
    cis: d.cis,
    version: d.version,
  };
}

/** Экранная форма «Инцидент» (Рис. 3.7). */
export function IncidentPage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const canEdit = useCanEdit();
  const ref = useReference();

  const details = useQuery({
    queryKey: ['incident', id],
    queryFn: () => get<IncidentDetails>(`/incidents/${id}`),
    enabled: !isNew,
  });

  const [form, setForm] = useState<FormState>(empty);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [ciSearch, setCiSearch] = useState('');
  const [ciPicker, setCiPicker] = useState(false);

  useEffect(() => {
    if (details.data) setForm(fromDetails(details.data));
  }, [details.data]);

  useEffect(() => {
    const data = ref.data;
    if (isNew && data && form.priorityId == null) {
      setForm((f) => ({
        ...f,
        priorityId: data.priorities.find((p) => p.code === '03')?.id ?? data.priorities[0]?.id,
        statusId: data.statuses[0]?.id,
      }));
    }
  }, [isNew, ref.data, form.priorityId]);

  const ciOptions = useQuery({
    queryKey: ['cis', 'pick', ciSearch],
    queryFn: () => get<Page<CiListItem>>(`/cis?size=20&q=${encodeURIComponent(ciSearch)}`),
    enabled: ciPicker,
  });

  const save = useMutation({
    mutationFn: (body: IncidentRequest) =>
      isNew ? post<{ id: number }>('/incidents', body) : put<void>(`/incidents/${id}`, body),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['incidents'] });
      qc.invalidateQueries({ queryKey: ['incident', id] });
      toast('Сохранено');
      setFieldErrors({});
      if (isNew && res) navigate(`/incidents/${(res as { id: number }).id}`, { replace: true });
    },
    onError: (e) => {
      if (e instanceof ApiError) setFieldErrors(e.fieldErrors);
      toast(errorText(e), 'error');
    },
  });

  if (!isNew && details.isLoading) return <Spinner />;
  if (details.error) return <ErrorBox error={details.error} />;
  if (!ref.data) return <Spinner />;
  const r = ref.data;
  const d = details.data;
  const priority = r.priorities.find((p) => p.id === form.priorityId);
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));

  const submit = (e?: FormEvent) => {
    e?.preventDefault();
    if (!form.priorityId || !form.statusId) return;
    save.mutate({
      title: form.title,
      description: form.description,
      resolution: form.resolution || null,
      registeredAt: fromLocalInput(form.registeredAt)!,
      firstResponseAt: fromLocalInput(form.firstResponseAt),
      resolvedAt: fromLocalInput(form.resolvedAt),
      closedAt: fromLocalInput(form.closedAt),
      priorityId: form.priorityId,
      statusId: form.statusId,
      assigneeId: form.assigneeId ?? null,
      ciIds: form.cis.map((c) => c.id),
      externalId: form.externalId || null,
      version: form.version,
    });
  };

  const err = (k: string) => fieldErrors[k] && <div className="field-error">{fieldErrors[k]}</div>;

  return (
    <>
      <PageHeader title={isNew ? 'Новый инцидент' : 'Инцидент'}>
        {canEdit && (
          <IconButton title="Сохранить" onClick={() => submit()} disabled={save.isPending}>
            {Icons.save}
          </IconButton>
        )}
      </PageHeader>
      <Card className="form-card">
        <form onSubmit={submit}>
          <fieldset disabled={!canEdit}>
            <div className="form-row">
              <span className="label">Идентификатор:</span>
              <input readOnly value={d?.code ?? 'будет присвоен при сохранении'} />
              {d?.source === 'IMPORT' && <span className="tag">импорт</span>}
            </div>
            <div className="form-row">
              <span className="label">Наименование:</span>
              <input required value={form.title} onChange={(e) => set('title', e.target.value)} />
            </div>
            {err('title')}
            <div className="form-row two">
              <label>
                <span className="label">Регистрация:</span>
                <input
                  type="datetime-local"
                  required
                  value={form.registeredAt}
                  onChange={(e) => set('registeredAt', e.target.value)}
                />
              </label>
              <label>
                <span className="label">Закрытие:</span>
                <input type="datetime-local" value={form.closedAt} onChange={(e) => set('closedAt', e.target.value)} />
              </label>
            </div>
            <div className="form-row two">
              <div>
                <span className="label">Приоритет:</span>
                <div className="radios">
                  {r.priorities.map((p) => (
                    <label key={p.id}>
                      <input type="radio" checked={form.priorityId === p.id} onChange={() => set('priorityId', p.id)} />
                      <span className="dot" style={{ background: p.color }} />
                      {p.name.toLowerCase()}
                    </label>
                  ))}
                </div>
                {priority && (
                  <div className="hint">
                    SLA: реакция {fmtMinutes(priority.responseSlaMinutes)}, решение{' '}
                    {fmtMinutes(priority.resolutionSlaMinutes)}
                  </div>
                )}
              </div>
              <div>
                <span className="label">Статус:</span>
                <div className="radios">
                  {r.statuses.map((s) => (
                    <label key={s.id}>
                      <input type="radio" checked={form.statusId === s.id} onChange={() => set('statusId', s.id)} />
                      {s.name.toLowerCase()}
                    </label>
                  ))}
                </div>
                <div className="hint">Моменты реакции, решения и закрытия проставятся по смене статуса.</div>
              </div>
            </div>
            <div className="form-row two">
              <label>
                <span className="label">Ответственный:</span>
                <select
                  value={form.assigneeId ?? ''}
                  onChange={(e) => set('assigneeId', e.target.value ? Number(e.target.value) : undefined)}
                >
                  <option value="">не назначен</option>
                  {r.employees
                    .filter((e) => e.active || e.id === form.assigneeId)
                    .map((e) => (
                      <option key={e.id} value={e.id}>
                        {e.postName} — {e.shortName}
                      </option>
                    ))}
                </select>
              </label>
              <div>
                <span className="label">КЕ:</span>
                <div className="chips">
                  {form.cis.map((c) => (
                    <span key={c.id} className="chip">
                      <Link to={`/cis/${c.id}`}>{c.name}</Link>
                      {canEdit && (
                        <button
                          type="button"
                          aria-label="Убрать"
                          onClick={() => set('cis', form.cis.filter((x) => x.id !== c.id))}
                        >
                          ×
                        </button>
                      )}
                    </span>
                  ))}
                  {canEdit && (
                    <button type="button" className="chip-add" onClick={() => setCiPicker(!ciPicker)}>
                      +
                    </button>
                  )}
                </div>
                {ciPicker && (
                  <div className="ci-picker">
                    <input autoFocus placeholder="Поиск КЕ" value={ciSearch} onChange={(e) => setCiSearch(e.target.value)} />
                    <ul>
                      {(ciOptions.data?.items ?? [])
                        .filter((c) => !form.cis.some((x) => x.id === c.id))
                        .map((c) => (
                          <li key={c.id}>
                            <button
                              type="button"
                              onClick={() => {
                                set('cis', [
                                  ...form.cis,
                                  { id: c.id, code: c.code, name: c.name, institutionName: c.institutionName },
                                ]);
                                setCiPicker(false);
                              }}
                            >
                              {c.name} <span className="muted">{c.code} · {c.institutionName}</span>
                            </button>
                          </li>
                        ))}
                    </ul>
                  </div>
                )}
              </div>
            </div>
            <div className="form-block">
              <span className="label">Описание:</span>
              <textarea required rows={6} value={form.description} onChange={(e) => set('description', e.target.value)} />
              {err('description')}
            </div>
            <div className="form-block">
              <span className="label">Решение:</span>
              <textarea rows={4} value={form.resolution} onChange={(e) => set('resolution', e.target.value)} />
            </div>

            <details className="form-block">
              <summary>Дополнительно: внешний ID и отметки SLA</summary>
              <div className="form-row two">
                <label>
                  <span className="label">ID в трекере:</span>
                  <input value={form.externalId} onChange={(e) => set('externalId', e.target.value)} />
                </label>
                <label>
                  <span className="label">Первая реакция:</span>
                  <input
                    type="datetime-local"
                    value={form.firstResponseAt}
                    onChange={(e) => set('firstResponseAt', e.target.value)}
                  />
                </label>
              </div>
              <div className="form-row two">
                <label>
                  <span className="label">Решён:</span>
                  <input type="datetime-local" value={form.resolvedAt} onChange={(e) => set('resolvedAt', e.target.value)} />
                </label>
                <div />
              </div>
            </details>
            <button type="submit" hidden />
          </fieldset>
        </form>
      </Card>

      {d && (
        <Card>
          <h3>История статусов</h3>
          <table className="grid compact">
            <thead>
              <tr>
                <th>Когда</th>
                <th>Изменение</th>
                <th>Кто</th>
              </tr>
            </thead>
            <tbody>
              {d.history.map((h, i) => (
                <tr key={i}>
                  <td>{fmtDateTime(h.changedAt)}</td>
                  <td>{h.fromStatus ? `${h.fromStatus} → ${h.toStatus}` : h.toStatus}</td>
                  <td>{h.changedBy ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted">
            Создан {fmtDateTime(d.createdAt)} ({d.createdBy ?? '—'}), изменён {fmtDateTime(d.updatedAt)} ({d.updatedBy ?? '—'})
          </p>
        </Card>
      )}
    </>
  );
}
