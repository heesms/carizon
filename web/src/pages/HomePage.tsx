import { Link } from 'react-router-dom';

export default function HomePage() {
  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">Welcome to Carizon</h1>
      <p className="text-slate-300">
        Discover cross-platform inventory, track price movements, and manage alerts for the
        vehicles you love.
      </p>
      <div className="flex gap-4">
        <Link to="/cars" className="rounded bg-sky-500 px-4 py-2 font-medium text-white">
          Browse Cars
        </Link>
        <Link to="/compare" className="rounded border border-slate-700 px-4 py-2">
          Compare Vehicles
        </Link>
      </div>
    </div>
  );
}
