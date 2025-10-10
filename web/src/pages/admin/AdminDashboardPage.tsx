import { Link } from 'react-router-dom';

const links = [
  { to: '/admin/ingest', label: 'Ingestion status' },
  { to: '/admin/mapping', label: 'Mapping queue' },
  { to: '/admin/qc', label: 'QC anomalies' },
  { to: '/admin/logs', label: 'Job logs' },
];

export default function AdminDashboardPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Admin console</h1>
      <p className="text-slate-400">Select a module to manage Carizon operations.</p>
      <ul className="grid gap-3 md:grid-cols-2">
        {links.map((link) => (
          <li key={link.to}>
            <Link
              to={link.to}
              className="block rounded border border-slate-800 bg-slate-900 p-4 hover:border-sky-500"
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
