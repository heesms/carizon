import { Link, Outlet } from 'react-router-dom'

type Props = {
  className?: string
}

export default function Layout({ className = '' }: Props) {
  return (
    <div className={`min-h-screen bg-white ${className}`}>
      <header className="border-b">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="font-bold text-lg">
            Carizon
          </Link>
          <nav className="flex gap-3 text-sm text-gray-600">
            <Link to="/">홈</Link>
            <Link to="/search">검색</Link>
            <Link to="/recommendation">추천</Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl p-4 sm:p-6">
        <Outlet />
      </main>
    </div>
  )
}
