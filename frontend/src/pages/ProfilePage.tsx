import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { del, get, post } from '../api';
import { useAuth } from '../auth';
import { Card, PageHeader, errorText, useToast } from '../components/ui';
import type { UserView } from '../types';

export function ProfilePage() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null);
  const [code, setCode] = useState('');

  const reloadMe = () => get<UserView>('/auth/me').then(setUser);

  const changePassword = useMutation({
    mutationFn: () => post('/auth/password', { currentPassword: current, newPassword: next }),
    onSuccess: () => {
      toast('Пароль изменён');
      setCurrent('');
      setNext('');
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  const startTotp = useMutation({
    mutationFn: () => post<{ secret: string; otpauthUri: string }>('/auth/totp/setup'),
    onSuccess: setSetup,
    onError: (e) => toast(errorText(e), 'error'),
  });
  const confirmTotp = useMutation({
    mutationFn: () => post('/auth/totp/confirm', { code }),
    onSuccess: async () => {
      toast('Двухфакторная аутентификация включена');
      setSetup(null);
      setCode('');
      await reloadMe();
    },
    onError: (e) => toast(errorText(e), 'error'),
  });
  const disableTotp = useMutation({
    mutationFn: () => del('/auth/totp', { code }),
    onSuccess: async () => {
      toast('Двухфакторная аутентификация отключена');
      setCode('');
      await reloadMe();
    },
    onError: (e) => toast(errorText(e), 'error'),
  });

  if (!user) return null;

  return (
    <>
      <PageHeader title="Профиль" />
      <Card>
        <dl className="report-facts">
          <dt>Логин:</dt>
          <dd className="mono">{user.username}</dd>
          <dt>Сотрудник:</dt>
          <dd>{user.employeeName ?? 'не связан (обратитесь к администратору)'}</dd>
          <dt>Должность:</dt>
          <dd>{user.postName ?? '—'}</dd>
        </dl>
      </Card>

      <Card>
        <h2>Смена пароля</h2>
        <form
          className="stack narrow"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            changePassword.mutate();
          }}
        >
          <input type="password" placeholder="Текущий пароль" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
          <input type="password" placeholder="Новый пароль (от 12 символов)" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
          <button className="primary" disabled={!current || !next || changePassword.isPending}>
            Сменить пароль
          </button>
        </form>
      </Card>

      <Card>
        <h2>Двухфакторная аутентификация</h2>
        {user.totpEnabled ? (
          <div className="stack narrow">
            <p>Включена. Для отключения введите текущий код из приложения.</p>
            <input inputMode="numeric" maxLength={6} placeholder="Код" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
            <button type="button" disabled={code.length !== 6} onClick={() => disableTotp.mutate()}>
              Отключить
            </button>
          </div>
        ) : setup ? (
          <div className="stack narrow">
            <p>
              Добавьте ключ в приложение-аутентификатор (Яндекс Ключ, Google Authenticator, Aegis и т.п.) вручную или по
              ссылке, затем введите шестизначный код.
            </p>
            <code className="secret">{setup.secret.match(/.{1,4}/g)?.join(' ')}</code>
            <a href={setup.otpauthUri} className="small">
              открыть в приложении
            </a>
            <input inputMode="numeric" maxLength={6} placeholder="Код" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
            <button className="primary" disabled={code.length !== 6 || confirmTotp.isPending} onClick={() => confirmTotp.mutate()}>
              Подтвердить
            </button>
          </div>
        ) : (
          <div className="stack narrow">
            <p>Второй фактор защищает вход, даже если пароль стал известен посторонним.</p>
            <button className="primary" onClick={() => startTotp.mutate()}>
              Включить
            </button>
          </div>
        )}
      </Card>
    </>
  );
}
