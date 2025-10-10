import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../services/apiClient';

export default function AdminIngestPage() {
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: ['admin', 'ingest'],
    queryFn: async () => {
      const response = await apiClient.get('/admin/ingest/status');
      return response.data.data ?? {};
    },
  });

  const mutation = useMutation({
    mutationFn: (platform: string) =>
      apiClient.post('/admin/ingest/run', undefined, { params: { platform } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'ingest'] }),
  });

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Ingestion status</h1>
      <pre className="rounded border border-slate-800 bg-slate-950 p-4 text-sm text-slate-300">
        {JSON.stringify(data, null, 2)}
      </pre>
      <div className="flex flex-wrap gap-2">
        {['ENCAR', 'KCAR', 'CHACHACHA', 'CHUTCHA', 'CHARANCHA'].map((platform) => (
          <button
            key={platform}
            className="rounded bg-sky-500 px-3 py-2 text-sm"
            onClick={() => mutation.mutate(platform)}
          >
            Trigger {platform}
          </button>
        ))}
      </div>
    </div>
  );
}
