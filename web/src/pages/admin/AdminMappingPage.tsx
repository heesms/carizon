import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../services/apiClient';

export default function AdminMappingPage() {
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: ['admin', 'mapping'],
    queryFn: async () => {
      const response = await apiClient.get('/admin/mapping/failures');
      return response.data.data ?? [];
    },
  });

  const mutation = useMutation({
    mutationFn: (payload: any) => apiClient.post('/admin/mapping/resolve', payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'mapping'] }),
  });

  const items = data ?? [];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Mapping failures</h1>
      <ul className="space-y-3">
        {items.map((item: any, index: number) => (
          <li key={index} className="rounded border border-slate-800 bg-slate-900 p-4">
            <pre className="text-xs text-slate-300">{JSON.stringify(item, null, 2)}</pre>
            <button
              className="mt-3 rounded bg-emerald-500 px-3 py-2 text-xs"
              onClick={() => mutation.mutate({ id: item.id })}
            >
              Mark resolved
            </button>
          </li>
        ))}
      </ul>
      {!items.length && <p className="text-slate-500">No mapping issues 🎉</p>}
    </div>
  );
}
