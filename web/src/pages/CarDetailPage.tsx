import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../services/apiClient';
import PriceHistoryChart from './components/PriceHistoryChart';

export default function CarDetailPage() {
  const { id } = useParams();
  const { data, isLoading } = useQuery({
    queryKey: ['car', id],
    queryFn: async () => {
      const response = await apiClient.get(`/cars/${id}`);
      return response.data.data;
    },
    enabled: Boolean(id),
  });

  if (isLoading) {
    return <div className="animate-pulse text-slate-400">Loading vehicle...</div>;
  }

  if (!data) {
    return <div className="text-slate-400">Vehicle not found.</div>;
  }

  const mileage = data.mileage ? `${data.mileage.toLocaleString()} km` : '—';
  const price = data.price ? `₩${data.price.toLocaleString()}` : '—';

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-3xl font-bold text-white">{data.name}</h1>
          <p className="text-slate-400">
            {data.year ?? '—'} · {mileage} · {data.fuel ?? '—'} · {data.transmission ?? '—'}
          </p>
        </div>
        <button className="rounded bg-sky-500 px-4 py-2 text-sm font-medium text-white">
          Add to favorites
        </button>
      </header>
      <section className="grid gap-4 md:grid-cols-2">
        {data.offers?.map((offer: any) => (
          <article key={offer.platform} className="rounded border border-slate-800 bg-slate-900 p-4">
            <h2 className="text-lg font-semibold text-slate-100">{offer.platform}</h2>
            <p className="mt-2 text-2xl font-bold text-sky-400">
              {offer.price ? `₩${offer.price.toLocaleString()}` : '—'}
            </p>
            <p className="text-sm text-slate-400">
              {offer.mileage ? `${offer.mileage.toLocaleString()} km` : '—'}
            </p>
            {offer.url && (
              <a
                href={offer.url}
                target="_blank"
                rel="noreferrer"
                className="mt-3 inline-block text-sm text-sky-400 hover:underline"
              >
                View listing
              </a>
            )}
          </article>
        ))}
      </section>
      <section className="rounded border border-slate-800 bg-slate-900 p-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-100">Price history</h2>
          <span className="text-sm text-slate-400">Latest price: {price}</span>
        </div>
        <PriceHistoryChart data={data.priceHistory || []} />
      </section>
    </div>
  );
}
