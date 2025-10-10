import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../services/apiClient';

export default function AdminQcPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'qc'],
    queryFn: async () => {
      const response = await apiClient.get('/admin/qc/anomalies');
      return response.data.data ?? [];
    },
  });

  const anomalies = data ?? [];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">QC anomalies</h1>
      {isLoading ? (
        <p className="text-slate-400">Loading anomalies…</p>
      ) : anomalies.length ? (
        <ul className="space-y-3">
          {anomalies.map((item: any, index: number) => (
            <li key={index} className="rounded border border-slate-800 bg-slate-900 p-4">
              <pre className="text-xs text-slate-300">{JSON.stringify(item, null, 2)}</pre>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-slate-500">No anomalies detected.</p>
      )}
    </div>
  );
}
