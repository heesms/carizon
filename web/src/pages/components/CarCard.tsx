import { Link } from 'react-router-dom';
import type { CarSummary } from '../../store/useCars';

interface Props {
  car: CarSummary;
}

export default function CarCard({ car }: Props) {
  const platforms = car.platforms?.length ? car.platforms : ['—'];
  const price = car.price ? `₩${car.price.toLocaleString()}` : '—';

  return (
    <Link
      to={`/cars/${car.id}`}
      className="rounded border border-slate-800 bg-slate-900 p-4 shadow hover:border-sky-500"
    >
      <div className="flex items-baseline justify-between">
        <h3 className="text-lg font-semibold">{car.name}</h3>
        <span className="text-sky-400">{price}</span>
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm text-slate-400">
        <div>
          <dt className="uppercase tracking-wide">Year</dt>
          <dd>{car.year ?? '—'}</dd>
        </div>
        <div>
          <dt className="uppercase tracking-wide">Mileage</dt>
          <dd>{car.mileage ? `${car.mileage.toLocaleString()} km` : '—'}</dd>
        </div>
        <div>
          <dt className="uppercase tracking-wide">Fuel</dt>
          <dd>{car.fuel ?? '—'}</dd>
        </div>
        <div>
          <dt className="uppercase tracking-wide">Transmission</dt>
          <dd>{car.transmission ?? '—'}</dd>
        </div>
      </dl>
      <p className="mt-4 text-xs text-slate-500">Platforms: {platforms.join(', ')}</p>
    </Link>
  );
}
