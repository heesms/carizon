import { Link } from 'react-router-dom';
import type { CarSummary } from '../../store/useCars';

interface Props {
  cars: CarSummary[];
}

export default function CarTable({ cars }: Props) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-800 text-sm">
        <thead className="bg-slate-900 text-slate-300">
          <tr>
            <th className="px-4 py-3 text-left">Vehicle</th>
            <th className="px-4 py-3 text-left">Year</th>
            <th className="px-4 py-3 text-left">Mileage</th>
            <th className="px-4 py-3 text-left">Fuel</th>
            <th className="px-4 py-3 text-left">Transmission</th>
            <th className="px-4 py-3 text-left">Price</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {cars.map((car) => (
            <tr key={car.id} className="hover:bg-slate-900">
              <td className="px-4 py-3">
                <Link to={`/cars/${car.id}`} className="text-sky-400 hover:underline">
                  {car.name}
                </Link>
              </td>
              <td className="px-4 py-3">{car.year ?? '—'}</td>
              <td className="px-4 py-3">
                {car.mileage ? `${car.mileage.toLocaleString()} km` : '—'}
              </td>
              <td className="px-4 py-3">{car.fuel ?? '—'}</td>
              <td className="px-4 py-3">{car.transmission ?? '—'}</td>
              <td className="px-4 py-3">{car.price ? `₩${car.price.toLocaleString()}` : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
