import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router';
import { useAuth } from './auth';
import { Layout } from './components/Layout';
import { Spinner } from './components/ui';
import { LoginPage } from './pages/LoginPage';
import { IncidentsPage } from './pages/IncidentsPage';
import { IncidentPage } from './pages/IncidentPage';
import { CisPage } from './pages/CisPage';
import { CiPage } from './pages/CiPage';
import { ReferencesPage } from './pages/ReferencesPage';
import { CategoriesPage } from './pages/CategoriesPage';
import { CategoryPage } from './pages/CategoryPage';
import { ReportsPage } from './pages/ReportsPage';
import { UsersPage } from './pages/UsersPage';
import { AuditPage } from './pages/AuditPage';
import { ProfilePage } from './pages/ProfilePage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  return can('ADMIN') ? <>{children}</> : <Navigate to="/incidents" replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/incidents" replace />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="incidents/:id" element={<IncidentPage />} />
        <Route path="cis" element={<CisPage />} />
        <Route path="cis/:id" element={<CiPage />} />
        <Route path="references" element={<ReferencesPage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="categories/:id" element={<CategoryPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route
          path="admin/users"
          element={
            <RequireAdmin>
              <UsersPage />
            </RequireAdmin>
          }
        />
        <Route
          path="admin/audit"
          element={
            <RequireAdmin>
              <AuditPage />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<Navigate to="/incidents" replace />} />
      </Route>
    </Routes>
  );
}
