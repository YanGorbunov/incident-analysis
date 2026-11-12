import { NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../auth';
import { Icons } from './ui';

/** Каркас по макетам ВКР: оранжевая плашка пользователя и серое меню слева. */
export function Layout() {
  const { user, logout, can } = useAuth();
  const navigate = useNavigate();
  const displayName = user?.employeeName ?? user?.username ?? '';

  const items: [string, string][] = [
    ['/incidents', 'Инциденты'],
    ['/categories', 'Категории инцидентов'],
    ['/references', 'Справочники'],
    ['/cis', 'КЕ'],
    ['/reports', 'Отчётность'],
  ];
  if (can('ADMIN')) {
    items.push(['/admin/users', 'Пользователи'], ['/admin/audit', 'Журнал событий']);
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="user-plate">
          <button type="button" className="user-name" onClick={() => navigate('/profile')} title="Профиль">
            {Icons.user}
            <span>{displayName}</span>
          </button>
          <button
            type="button"
            className="logout"
            title="Выйти"
            aria-label="Выйти"
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
          >
            {Icons.logout}
          </button>
        </div>
        <nav>
          {items.map(([to, label]) => (
            <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'active' : '')}>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot muted">{user?.role === 'VIEWER' ? 'Режим просмотра' : ''}</div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
