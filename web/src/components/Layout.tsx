import { Link, Outlet } from 'react-router-dom';
import { useAuthGuard } from '../hooks/useAuthGuard';

export function Layout() {
  const { isAdmin } = useAuthGuard();

  return (
    <div className="min-h-screen bg-slate-950 text-slate-50">
      <header className="border-b border-slate-800 bg-slate-900">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Link to="/" className="text-xl font-semibold">
            Carizon
          </Link>
          <nav className="flex gap-4 text-sm">
            <Link to="/cars" className="hover:text-white">
              Cars
            </Link>
            <Link to="/compare" className="hover:text-white">
              Compare
            </Link>
            <Link to="/favorites" className="hover:text-white">
              Favorites
            </Link>
            <Link to="/alerts" className="hover:text-white">
              Alerts
            </Link>
            {isAdmin && (
              <Link to="/admin" className="hover:text-white">
                Admin
              </Link>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-10">
        <Outlet />
      </main>
    </div>
  );
}
