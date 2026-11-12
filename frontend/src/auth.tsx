import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, get, loadCsrf, post } from './api';
import type { Role, UserView } from './types';

interface AuthState {
  user: UserView | null;
  loading: boolean;
  setUser: (u: UserView | null) => void;
  logout: () => Promise<void>;
  can: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null);
  const [loading, setLoading] = useState(true);
  const queryClient = useQueryClient();

  useEffect(() => {
    loadCsrf()
      .then(() => get<UserView>('/auth/me'))
      .then(setUser)
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) console.error(e);
        setUser(null);
      })
      .finally(() => setLoading(false));

    const expired = () => setUser(null);
    window.addEventListener('auth:expired', expired);
    return () => window.removeEventListener('auth:expired', expired);
  }, []);

  const logout = useCallback(async () => {
    try {
      await post('/auth/logout');
    } finally {
      setUser(null);
      queryClient.clear();
      await loadCsrf().catch(() => undefined);
    }
  }, [queryClient]);

  const can = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user]);

  return (
    <AuthContext.Provider value={{ user, loading, setUser, logout, can }}>{children}</AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth вне AuthProvider');
  return ctx;
}

/** Может ли пользователь менять данные (аналитик или администратор). */
export function useCanEdit(): boolean {
  return useAuth().can('ADMIN', 'ANALYST');
}
