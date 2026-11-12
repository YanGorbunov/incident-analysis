import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { get } from '../api';
import { COLUMNS, FieldPicker, IncidentTable } from '../components/IncidentTable';
import { Card, ErrorBox, PageHeader, Spinner } from '../components/ui';
import { fmtDuration, fmtPct, pct } from '../format';
import type { Category, GroupStat, Report, ReportField } from '../types';

const MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
const REPORT_DEFAULT: ReportField[] = ['CODE', 'TITLE', 'REGISTERED_AT', 'CLOSED_AT', 'PRIORITY', 'STATUS', 'ASSIGNEE'];

function titleDate(iso: string) {
  const d = new Date(iso);
  return `«${d.getDate()}» ${MONTHS[d.getMonth()]} ${d.getFullYear()} г.`;
}

/** Раздел «Отчётность» (Рис. 3.13) и сводный отчёт (Рис. 2.6) с расширенной аналитикой. */
export function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const selected = params.get('category');
  const [choice, setChoice] = useState(selected ?? '');
  const [format, setFormat] = useState<'DOCX' | 'XLSX'>('DOCX');
  const [fields, setFields] = useState<ReportField[]>(REPORT_DEFAULT);

  const categories = useQuery({ queryKey: ['categories'], queryFn: () => get<Category[]>('/categories') });
  const report = useQuery({
    queryKey: ['report', selected],
    queryFn: () => get<Report>(`/reports/${selected}`),
    enabled: !!selected,
  });

  const exportUrl = () => {
    const q = new URLSearchParams({ format });
    fields.forEach((f) => q.append('fields', f));
    return `/api/reports/${selected}/export?${q}`;
  };

  return (
    <>
      <PageHeader title="Отчётность" />
      <div className="report-controls">
        <Card className="inline-card">
          <label className="inline">
            Категория:
            <select value={choice} onChange={(e) => setChoice(e.target.value)}>
              <option value="">выберите</option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="primary"
            disabled={!choice}
            onClick={() => (choice === selected ? report.refetch() : setParams({ category: choice }))}
          >
            Сгенерировать
          </button>
        </Card>
        <Card className="inline-card">
          <label className="inline">
            Формат:
            <select value={format} onChange={(e) => setFormat(e.target.value as 'DOCX' | 'XLSX')}>
              <option>DOCX</option>
              <option>XLSX</option>
            </select>
          </label>
          <a className={`button primary ${report.data ? '' : 'disabled'}`} href={report.data ? exportUrl() : undefined} download>
            Экспортировать
          </a>
        </Card>
        <Card className="inline-card">
          <FieldPicker value={fields} onChange={setFields} />
          <span className="muted small">{COLUMNS.filter((c) => fields.includes(c.key)).map((c) => c.title).join(', ')}</span>
        </Card>
      </div>

      {report.isFetching && <Spinner />}
      <ErrorBox error={report.error} />
      {!selected && <Card><p className="muted">Выберите категорию и нажмите «Сгенерировать».</p></Card>}
      {report.data && <ReportView report={report.data} fields={fields} />}
    </>
  );
}

function ReportView({ report, fields }: { report: Report; fields: ReportField[] }) {
  const s = report.summary;
  const resSla = pct(s.resolutionSlaTotal - s.resolutionSlaBreached, s.resolutionSlaTotal);
  const respSla = pct(s.responseSlaTotal - s.responseSlaBreached, s.responseSlaTotal);
  const pie = report.byPriority.filter((p) => p.total > 0);

  return (
    <Card className="report">
      <h2 className="report-title">Сводный отчёт о результатах анализа инцидентов от {titleDate(report.generatedAt)}</h2>
      <div className="report-head">
        <dl className="report-facts">
          <dt>Должность:</dt>
          <dd>{report.author.post ?? '—'}</dd>
          <dt>Сотрудник:</dt>
          <dd>{report.author.name ?? '—'}</dd>
          <dt>Категория:</dt>
          <dd>{report.category.name}</dd>
          <dt>Количество инцидентов:</dt>
          <dd>{s.total}</dd>
          <dt>Среднее время разрешения:</dt>
          <dd>{fmtDuration(s.avgResolutionSeconds)}</dd>
          <dt>Процент закрытых инцидентов:</dt>
          <dd>{fmtPct(s.closedPct)}</dd>
        </dl>
        <div className="pie">
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie data={pie} dataKey="total" nameKey="name" outerRadius={95} isAnimationActive={false}>
                {pie.map((p) => (
                  <Cell key={p.id} fill={p.color} />
                ))}
              </Pie>
              <Tooltip />
              <Legend layout="vertical" align="right" verticalAlign="middle" />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="kpis">
        <Kpi title="Медиана решения" value={fmtDuration(s.medianResolutionSeconds)} hint="половина инцидентов решается быстрее" />
        <Kpi title="90-й перцентиль" value={fmtDuration(s.p90ResolutionSeconds)} hint="9 из 10 укладываются" />
        <Kpi title="Среднее время реакции" value={fmtDuration(s.avgResponseSeconds)} />
        <Kpi
          title="SLA решения"
          value={fmtPct(resSla)}
          hint={`нарушений ${s.resolutionSlaBreached} из ${s.resolutionSlaTotal}`}
          bad={resSla != null && resSla < 90}
        />
        <Kpi
          title="SLA реакции"
          value={fmtPct(respSla)}
          hint={`нарушений ${s.responseSlaBreached} из ${s.responseSlaTotal}`}
          bad={respSla != null && respSla < 90}
        />
      </div>

      {report.trend.length > 1 && (
        <section>
          <h3>Динамика по неделям</h3>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={report.trend}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="weekStart" tickFormatter={(v: string) => v.slice(5).split('-').reverse().join('.')} />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="registered" name="Зарегистрировано" stroke="#FF9500" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="closed" name="Закрыто" stroke="#1F4E9A" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </section>
      )}

      <div className="two-charts">
        <GroupChart title="По ведомствам" data={report.byInstitution} />
        <GroupChart title="КЕ с наибольшим числом инцидентов" data={report.topCis} />
      </div>
      <GroupChart title="По ответственным" data={report.byAssignee} />

      <h3>Инциденты</h3>
      <IncidentTable items={report.incidents} fields={fields} />
    </Card>
  );
}

function Kpi({ title, value, hint, bad }: { title: string; value: string; hint?: string; bad?: boolean }) {
  return (
    <div className={`kpi ${bad ? 'kpi-bad' : ''}`}>
      <div className="kpi-title">{title}</div>
      <div className="kpi-value">{value}</div>
      {hint && <div className="kpi-hint">{hint}</div>}
    </div>
  );
}

function GroupChart({ title, data }: { title: string; data: GroupStat[] }) {
  if (data.length === 0) return null;
  const rows = data.map((g) => ({ name: g.name, 'В норме': g.total - g.slaBreached, 'Нарушен SLA': g.slaBreached }));
  return (
    <section>
      <h3>{title}</h3>
      <ResponsiveContainer width="100%" height={Math.max(160, rows.length * 32 + 40)}>
        <BarChart data={rows} layout="vertical" margin={{ left: 20 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" allowDecimals={false} />
          <YAxis type="category" dataKey="name" width={230} tick={{ fontSize: 12 }} />
          <Tooltip />
          <Legend />
          <Bar dataKey="В норме" stackId="a" fill="#1F4E9A" />
          <Bar dataKey="Нарушен SLA" stackId="a" fill="#E4572E" />
        </BarChart>
      </ResponsiveContainer>
    </section>
  );
}
