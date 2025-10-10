import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-3xl font-semibold">Page not found</h1>
      <p className="text-slate-400">The page you requested does not exist.</p>
      <Link to="/" className="text-sky-400 hover:underline">
        Go back home
      </Link>
    </div>
  );
}
