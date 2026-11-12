const dtf = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  year: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function fmtDateTime(iso?: string | null): string {
  return iso ? dtf.format(new Date(iso)) : '—';
}

/** «5 дн. 1 ч. 10 мин.» — как в сводном отчёте ВКР. */
export function fmtDuration(seconds?: number | null): string {
  if (seconds == null) return '—';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const parts: string[] = [];
  if (days > 0) parts.push(`${days} дн.`);
  if (days > 0 || hours > 0) parts.push(`${hours} ч.`);
  parts.push(`${minutes} мин.`);
  return parts.join(' ');
}

export function fmtMinutes(min?: number | null): string {
  return min == null ? '—' : fmtDuration(min * 60);
}

export function fmtPct(value?: number | null): string {
  return value == null ? '—' : `${value.toFixed(1).replace('.', ',')} %`;
}

export function pct(part: number, total: number): number | undefined {
  return total === 0 ? undefined : (100 * part) / total;
}

/** ISO → значение для <input type="datetime-local"> в локальном времени. */
export function toLocalInput(iso?: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Значение datetime-local → ISO (UTC), который понимает OffsetDateTime. */
export function fromLocalInput(value: string): string | null {
  return value ? new Date(value).toISOString() : null;
}
