import { useState } from 'react';

export default function FiltersPanel() {
  const [expanded, setExpanded] = useState(true);

  return (
    <section className="rounded border border-slate-800 bg-slate-900 p-4 text-sm">
      <button
        className="flex w-full items-center justify-between"
        onClick={() => setExpanded((prev) => !prev)}
      >
        <span className="font-medium text-slate-200">Filters</span>
        <span>{expanded ? '−' : '+'}</span>
      </button>
      {expanded && (
        <div className="mt-4 grid grid-cols-2 gap-4 md:grid-cols-4">
          {['Price', 'Mileage', 'Fuel', 'Transmission', 'Body', 'Region'].map((label) => (
            <label key={label} className="flex flex-col gap-1">
              <span className="text-xs uppercase text-slate-400">{label}</span>
              <input
                className="rounded border border-slate-700 bg-slate-950 px-2 py-1 text-slate-200"
                placeholder={`Any ${label}`}
              />
            </label>
          ))}
        </div>
      )}
    </section>
  );
}
