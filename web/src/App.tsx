import { Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthGuard } from './hooks/useAuthGuard';
import { Layout } from './components/Layout';
import HomePage from './pages/HomePage';
import CarsPage from './pages/CarsPage';
import CarDetailPage from './pages/CarDetailPage';
import ComparePage from './pages/ComparePage';
import FavoritesPage from './pages/FavoritesPage';
import AlertsPage from './pages/AlertsPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminIngestPage from './pages/admin/AdminIngestPage';
import AdminMappingPage from './pages/admin/AdminMappingPage';
import AdminQcPage from './pages/admin/AdminQcPage';
import AdminLogsPage from './pages/admin/AdminLogsPage';
import NotFoundPage from './pages/NotFoundPage';

const queryClient = new QueryClient();

function App() {
  const { isAdmin } = useAuthGuard();

  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div className="p-8">Loading...</div>}>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<HomePage />} />
            <Route path="cars" element={<CarsPage />} />
            <Route path="cars/:id" element={<CarDetailPage />} />
            <Route path="compare" element={<ComparePage />} />
            <Route path="favorites" element={<FavoritesPage />} />
            <Route path="alerts" element={<AlertsPage />} />
            <Route
              path="admin"
              element={isAdmin ? <AdminDashboardPage /> : <Navigate to="/" replace />}
            />
            <Route
              path="admin/ingest"
              element={isAdmin ? <AdminIngestPage /> : <Navigate to="/" replace />}
            />
            <Route
              path="admin/mapping"
              element={isAdmin ? <AdminMappingPage /> : <Navigate to="/" replace />}
            />
            <Route
              path="admin/qc"
              element={isAdmin ? <AdminQcPage /> : <Navigate to="/" replace />}
            />
            <Route
              path="admin/logs"
              element={isAdmin ? <AdminLogsPage /> : <Navigate to="/" replace />}
            />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
    </QueryClientProvider>
  );
}

export default App;
