import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../services/apiClient';

export default function FavoritesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['favorites'],
    queryFn: async () => {
      const response = await apiClient.get('/favorites');
      return response.data.data ?? [];
    },
  });

  const favorites = data ?? [];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Favorites</h1>
      {isLoading ? (
        <p className="text-slate-400">Loading favorites…</p>
      ) : favorites.length ? (
        <ul className="space-y-3">
          {favorites.map((item: any) => (
            <li key={item.id} className="rounded border border-slate-800 bg-slate-900 p-4">
              Favorite #{item.id} for car {item.carId}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-slate-500">No favorites yet.</p>
      )}
    </div>
  );
}
