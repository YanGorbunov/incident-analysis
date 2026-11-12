// Тонкая обёртка над fetch: cookie-сессия, CSRF-заголовок, ошибки RFC 9457.

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function cookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(name + '='))
    ?.substring(name.length + 1);
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

export async function api<T>(path: string, method: Method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    const token = cookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch('/api' + path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401 && !path.startsWith('/auth/')) {
    window.dispatchEvent(new Event('auth:expired'));
  }
  if (!res.ok) {
    let message = `Ошибка ${res.status}`;
    let fieldErrors: Record<string, string> = {};
    try {
      const problem = await res.json();
      message = problem.detail ?? problem.title ?? message;
      fieldErrors = problem.errors ?? {};
    } catch {
      /* тело не JSON */
    }
    if (res.status === 403) message = 'Недостаточно прав для этого действия';
    throw new ApiError(res.status, message, fieldErrors);
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Получить cookie XSRF-TOKEN (вызывается при старте и после выхода). */
export function loadCsrf(): Promise<unknown> {
  return api('/auth/csrf');
}

export const get = <T>(path: string) => api<T>(path);
export const post = <T>(path: string, body?: unknown) => api<T>(path, 'POST', body ?? {});
export const put = <T>(path: string, body: unknown) => api<T>(path, 'PUT', body);
export const del = <T>(path: string, body?: unknown) => api<T>(path, 'DELETE', body);
