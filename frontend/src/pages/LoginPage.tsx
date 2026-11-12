import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { ApiError, post } from '../api';
import { useAuth } from '../auth';
import type { UserView } from '../types';

interface LoginResponse {
  mfaRequired: boolean;
  user?: UserView;
}

/** Экранная форма авторизации (Рис. 3.5) + шаг второго фактора. */
export function LoginPage() {
  const { user, setUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [mfa, setMfa] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const from = (location.state as { from?: string } | null)?.from ?? '/incidents';
  if (user) return <Navigate to={from} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = mfa
        ? await post<LoginResponse>('/auth/mfa', { code })
        : await post<LoginResponse>('/auth/login', { username, password });
      if (res.mfaRequired) {
        setMfa(true);
      } else if (res.user) {
        setUser(res.user);
        navigate(from, { replace: true });
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервер недоступен');
      if (mfa && err instanceof ApiError && err.message.includes('истекла')) setMfa(false);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <div className="login-logo">⚠</div>
        <h1>Учёт и анализ инцидентов</h1>
        {!mfa ? (
          <>
            <label>
              Логин
              <input autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
            </label>
            <label>
              Пароль
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
          </>
        ) : (
          <label>
            Код из приложения-аутентификатора
            <input
              autoFocus
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            />
          </label>
        )}
        {error && <div className="error-box">{error}</div>}
        <button className="primary" disabled={busy || (!mfa && (!username || !password)) || (mfa && code.length !== 6)}>
          {mfa ? 'Подтвердить' : 'Войти'}
        </button>
      </form>
    </div>
  );
}
