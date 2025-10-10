import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../services/apiClient';

export default function AlertsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['alerts'],
    queryFn: async () => {
      const response = await apiClient.get('/alerts');
      return response.data.data ?? [];
    },
  });

  const alerts = data ?? [];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Alerts</h1>
      {isLoading ? (
        <p className="text-slate-400">Loading alerts…</p>
      ) : alerts.length ? (
        <ul className="space-y-3">
          {alerts.map((item: any) => (
            <li key={item.id} className="rounded border border-slate-800 bg-slate-900 p-4">
              Alert #{item.id}: {item.criteria}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-slate-500">No alerts yet.</p>
      )}
    </div>
  );
}
