import { useQuery } from '@tanstack/react-query';
import { get } from '../api';
import { fromLocalInput, toLocalInput } from '../format';
import type { CiListItem, IncidentCriteria, Page } from '../types';
import { CheckList, MultiSelect, useReference } from './ui';

/** Параметры выделения категории (Рис. 3.12) + расширения: ведомство, SLA, текст. */
export function CriteriaForm({
  value,
  onChange,
}: {
  value: IncidentCriteria;
  onChange: (c: IncidentCriteria) => void;
}) {
  const ref = useReference();
  const cis = useQuery({
    queryKey: ['cis', 'all'],
    queryFn: () => get<Page<CiListItem>>('/cis?size=200'),
    staleTime: 60_000,
  });
  const set = <K extends keyof IncidentCriteria>(k: K, v: IncidentCriteria[K]) => onChange({ ...value, [k]: v });

  if (!ref.data) return null;
  const r = ref.data;

  return (
    <div className="criteria">
      <div className="criteria-col">
        <label className="row-label">
          С:
          <input
            type="datetime-local"
            value={toLocalInput(value.registeredFrom)}
            onChange={(e) => set('registeredFrom', fromLocalInput(e.target.value) ?? undefined)}
          />
        </label>
        <label className="row-label">
          По:
          <input
            type="datetime-local"
            value={toLocalInput(value.registeredTo)}
            onChange={(e) => set('registeredTo', fromLocalInput(e.target.value) ?? undefined)}
          />
        </label>
        <label className="row-label">
          Текст:
          <input
            placeholder="код, слова из названия или описания"
            value={value.q ?? ''}
            onChange={(e) => set('q', e.target.value || undefined)}
          />
        </label>
        <label className="row-label">
          SLA:
          <select
            value={value.slaBreached == null ? '' : String(value.slaBreached)}
            onChange={(e) => set('slaBreached', e.target.value === '' ? undefined : e.target.value === 'true')}
          >
            <option value="">Все</option>
            <option value="true">Только с нарушением SLA</option>
            <option value="false">Только без нарушения</option>
          </select>
        </label>
      </div>
      <div className="criteria-col">
        <div className="group-title">Приоритет:</div>
        <CheckList items={r.priorities} selected={value.priorityIds} onChange={(v) => set('priorityIds', v)} label={(p) => p.name} />
      </div>
      <div className="criteria-col">
        <div className="group-title">Статус:</div>
        <CheckList items={r.statuses} selected={value.statusIds} onChange={(v) => set('statusIds', v)} label={(s) => s.name} />
      </div>
      <div className="criteria-col wide">
        <label className="row-label">
          Ответственный:
          <MultiSelect
            items={r.employees}
            selected={value.assigneeIds}
            onChange={(v) => set('assigneeIds', v)}
            label={(e) => `${e.shortName} — ${e.postName}`}
          />
        </label>
        <label className="check-inline">
          <input
            type="checkbox"
            checked={!!value.unassigned}
            onChange={(e) => set('unassigned', e.target.checked || undefined)}
          />
          включая неназначенные
        </label>
        <label className="row-label">
          КЕ:
          <MultiSelect
            items={cis.data?.items ?? []}
            selected={value.ciIds}
            onChange={(v) => set('ciIds', v)}
            label={(c) => `${c.name} (${c.code})`}
          />
        </label>
        <label className="row-label">
          Ведомство:
          <MultiSelect
            items={r.institutions}
            selected={value.institutionIds}
            onChange={(v) => set('institutionIds', v)}
            label={(i) => i.name}
          />
        </label>
      </div>
    </div>
  );
}

/** Убрать пустые поля перед отправкой/сохранением. */
export function cleanCriteria(c: IncidentCriteria): IncidentCriteria {
  return Object.fromEntries(
    Object.entries(c).filter(([, v]) => v !== undefined && v !== null && v !== '' && !(Array.isArray(v) && v.length === 0)),
  ) as IncidentCriteria;
}
