import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router';
import { ApiError, get, post, put } from '../api';
import { useCanEdit } from '../auth';
import { Card, ErrorBox, IconButton, Icons, MultiSelect, PageHeader, Spinner, errorText, useReference, useToast } from '../components/ui';
import type { CiDetails } from '../types';

interface FormState {
  name: string;
  ciTypeId?: number;
  institutionId?: number;
  ipAddress: string;
  technicalSpecs: string;
  softwareIds: number[];
  externalId: string;
  version?: number;
}

/** Экранная форма «Конфигурационная единица» (Рис. 3.10). */
export function CiPage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const canEdit = useCanEdit();
  const ref = useReference();
  const [form, setForm] = useState<FormState>({ name: '', ipAddress: '', technicalSpecs: '', softwareIds: [], externalId: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const details = useQuery({
    queryKey: ['ci', id],
    queryFn: () => get<CiDetails>(`/cis/${id}`),
    enabled: !isNew,
  });

  useEffect(() => {
    const d = details.data;
    if (d) {
      setForm({
        name: d.name,
        ciTypeId: d.ciTypeId,
        institutionId: d.institutionId,
        ipAddress: d.ipAddress ?? '',
        technicalSpecs: d.technicalSpecs ?? '',
        softwareIds: d.software.map((s) => s.id),
        externalId: d.externalId ?? '',
        version: d.version,
      });
    }
  }, [details.data]);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: form.name,
        ciTypeId: form.ciTypeId,
        institutionId: form.institutionId,
        ipAddress: form.ipAddress || null,
        technicalSpecs: form.technicalSpecs || null,
        softwareIds: form.softwareIds,
        externalId: form.externalId || null,
        version: form.version,
      };
      return isNew ? post<{ id: number }>('/cis', body) : put<void>(`/cis/${id}`, body);
    },
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['cis'] });
      qc.invalidateQueries({ queryKey: ['ci', id] });
      toast('Сохранено');
      setFieldErrors({});
      if (isNew && res) navigate(`/cis/${(res as { id: number }).id}`, { replace: true });
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
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));
  const submit = (e?: FormEvent) => {
    e?.preventDefault();
    save.mutate();
  };

  return (
    <>
      <PageHeader title="Конфигурационная единица">
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
              <input readOnly value={details.data?.code ?? 'будет присвоен при сохранении (код ведомства + номер)'} />
            </div>
            <div className="form-row">
              <span className="label">Наименование:</span>
              <input required value={form.name} onChange={(e) => set('name', e.target.value)} />
            </div>
            <div className="form-row two">
              <label>
                <span className="label">Тип:</span>
                <select required value={form.ciTypeId ?? ''} onChange={(e) => set('ciTypeId', Number(e.target.value))}>
                  <option value="" disabled>
                    выберите
                  </option>
                  {r.ciTypes.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span className="label">IP-адрес:</span>
                <input value={form.ipAddress} onChange={(e) => set('ipAddress', e.target.value)} placeholder="10.0.0.1" />
                {fieldErrors.ipAddress && <div className="field-error">{fieldErrors.ipAddress}</div>}
              </label>
            </div>
            <div className="form-row">
              <span className="label">Ведомство:</span>
              <select
                required
                value={form.institutionId ?? ''}
                onChange={(e) => set('institutionId', Number(e.target.value))}
              >
                <option value="" disabled>
                  выберите
                </option>
                {r.institutions.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-block">
              <span className="label">Технические характеристики:</span>
              <textarea rows={6} value={form.technicalSpecs} onChange={(e) => set('technicalSpecs', e.target.value)} />
            </div>
            <div className="form-block">
              <span className="label">Программное обеспечение:</span>
              <MultiSelect
                items={r.software}
                selected={form.softwareIds}
                onChange={(v) => set('softwareIds', v ?? [])}
                label={(s) => `${s.name}${s.version ? ' ' + s.version : ''}`}
                placeholder="не указано"
              />
            </div>
            <div className="form-row">
              <span className="label">ID в трекере:</span>
              <input value={form.externalId} onChange={(e) => set('externalId', e.target.value)} />
            </div>
            <button type="submit" hidden />
          </fieldset>
        </form>
      </Card>
    </>
  );
}
