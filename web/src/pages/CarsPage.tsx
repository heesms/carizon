import { useState } from 'react';
import { useCars } from '../store/useCars';
import CarCard from './components/CarCard';
import CarTable from './components/CarTable';
import FiltersPanel from './components/FiltersPanel';

export default function CarsPage() {
  const [view, setView] = useState<'card' | 'table'>('card');
  const { cars, isLoading } = useCars();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Inventory</h1>
        <div className="flex rounded border border-slate-700">
          <button
            className={`px-4 py-2 text-sm ${view === 'card' ? 'bg-slate-800' : ''}`}
            onClick={() => setView('card')}
          >
            Cards
          </button>
          <button
            className={`px-4 py-2 text-sm ${view === 'table' ? 'bg-slate-800' : ''}`}
            onClick={() => setView('table')}
          >
            Table
          </button>
        </div>
      </div>
      <FiltersPanel />
      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-3">
          {Array.from({ length: 6 }).map((_, idx) => (
            <div key={idx} className="h-48 animate-pulse rounded bg-slate-800" />
          ))}
        </div>
      ) : view === 'card' ? (
        <div className="grid gap-4 md:grid-cols-3">
          {cars.map((car) => (
            <CarCard key={car.id} car={car} />
          ))}
        </div>
      ) : (
        <CarTable cars={cars} />
      )}
    </div>
  );
}
