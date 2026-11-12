import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { del, get, post, put } from '../api';
import { useAuth } from '../auth';
import { fmtDateTime } from '../format';
import { Card, ErrorBox, IconButton, Icons, Modal, PageHeader, errorText, useReference, useToast } from '../components/ui';
import type { Role, UserView } from '../types';

const ROLES: { value: Role; label: string }[] = [
  { value: 'ANALYST', label: 'Аналитик' },
  { value: 'VIEWER', label: 'Только просмотр' },
  { value: 'ADMIN', label: 'Администратор' },
];

export function UsersPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const { user: me } = useAuth();
  const users = useQuery({ queryKey: ['users'], queryFn: () => get<UserView[]>('/admin/users') });
  const [editing, setEditing] = useState<UserView | 'new' | null>(null);
  const [resetFor, setResetFor] = useState<UserView | null>(null);

  const action = useMutation({
    mutationFn: ({ path, method }: { path: string; method: 'POST' | 'DELETE' }) =>
      method === 'POST' ? post(path) : del(path),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      toast('Готово');
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  return (
    <>
      <PageHeader title="Пользователи">
        <IconButton title="Добавить пользователя" onClick={() => setEditing('new')}>
          {Icons.plus}
        </IconButton>
      </PageHeader>
      <Card>
        <ErrorBox error={users.error} />
        <div className="table-wrap">
          <table className="grid">
            <thead>
              <tr>
                <th>Логин</th>
                <th>Роль</th>
                <th>Сотрудник</th>
                <th>2FA</th>
                <th>Состояние</th>
                <th>Создан</th>
                <th className="actions-col">Действия</th>
              </tr>
            </thead>
            <tbody>
              {users.data?.map((u) => {
                const locked = u.lockedUntil && new Date(u.lockedUntil) > new Date();
                return (
                  <tr key={u.id}>
                    <td className="mono">{u.username}</td>
                    <td>{ROLES.find((r) => r.value === u.role)?.label}</td>
                    <td>{u.employeeName ? `${u.employeeName} (${u.postName})` : '—'}</td>
                    <td>{u.totpEnabled ? 'включена' : '—'}</td>
                    <td>{!u.enabled ? 'отключён' : locked ? `заблокирован до ${fmtDateTime(u.lockedUntil)}` : 'активен'}</td>
                    <td>{fmtDateTime(u.createdAt)}</td>
                    <td className="actions-col wide-actions">
                      <IconButton title="Изменить" onClick={() => setEditing(u)}>
                        {Icons.edit}
                      </IconButton>
                      <button type="button" className="link" onClick={() => setResetFor(u)}>
                        пароль
                      </button>
                      {locked && (
                        <button type="button" className="link" onClick={() => action.mutate({ path: `/admin/users/${u.id}/unlock`, method: 'POST' })}>
                          разблокировать
                        </button>
                      )}
                      {u.totpEnabled && (
                        <button
                          type="button"
                          className="link"
                          onClick={() => confirm('Сбросить второй фактор? Пользователь войдёт только по паролю.') &&
                            action.mutate({ path: `/admin/users/${u.id}/totp`, method: 'DELETE' })}
                        >
                          сброс 2FA
                        </button>
                      )}
                      {u.username !== me?.username && (
                        <IconButton
                          title="Удалить"
                          onClick={() => confirm(`Удалить пользователя ${u.username}?`) &&
                            action.mutate({ path: `/admin/users/${u.id}`, method: 'DELETE' })}
                        >
                          {Icons.trash}
                        </IconButton>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
      {editing && <UserEditor user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {resetFor && <PasswordReset user={resetFor} onClose={() => setResetFor(null)} />}
    </>
  );
}

function UserEditor({ user, onClose }: { user: UserView | null; onClose: () => void }) {
  const qc = useQueryClient();
  const ref = useReference();
  const [username, setUsername] = useState(user?.username ?? '');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>(user?.role ?? 'ANALYST');
  const [enabled, setEnabled] = useState(user?.enabled ?? true);
  const [employeeId, setEmployeeId] = useState<number | ''>(user?.employeeId ?? '');
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      user
        ? put(`/admin/users/${user.id}`, { role, enabled, employeeId: employeeId || null })
        : post('/admin/users', { username, password, role, employeeId: employeeId || null }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
    onError: (e) => setError(errorText(e)),
  });

  return (
    <Modal title={user ? `Пользователь ${user.username}` : 'Новый пользователь'} onClose={onClose}>
      <form
        className="modal-form"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        {!user && (
          <>
            <label>
              <span>Логин *</span>
              <input required value={username} onChange={(e) => setUsername(e.target.value)} />
            </label>
            <label>
              <span>Пароль * (не короче 12 символов)</span>
              <input required type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
          </>
        )}
        <label>
          <span>Роль</span>
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Сотрудник (для шапки отчёта: должность и ФИО)</span>
          <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">не связан</option>
            {ref.data?.employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.shortName} — {e.postName}
              </option>
            ))}
          </select>
        </label>
        {user && (
          <label className="check-inline">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            Учётная запись активна
          </label>
        )}
        {error && <div className="error-box">{error}</div>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>
            Отмена
          </button>
          <button className="primary" disabled={save.isPending}>
            Сохранить
          </button>
        </div>
      </form>
    </Modal>
  );
}

function PasswordReset({ user, onClose }: { user: UserView; onClose: () => void }) {
  const toast = useToast();
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: () => post(`/admin/users/${user.id}/password`, { password }),
    onSuccess: () => {
      toast('Пароль изменён');
      onClose();
    },
    onError: (e) => setError(errorText(e)),
  });
  return (
    <Modal title={`Новый пароль для ${user.username}`} onClose={onClose}>
      <form
        className="modal-form"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <label>
          <span>Пароль (не короче 12 символов)</span>
          <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="error-box">{error}</div>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>
            Отмена
          </button>
          <button className="primary" disabled={!password || save.isPending}>
            Сохранить
          </button>
        </div>
      </form>
    </Modal>
  );
}
