import { Link, Outlet } from 'react-router-dom'

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="sticky top-0 z-20 border-b border-white/20 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="font-bold text-lg">
            Carizon
          </Link>
          <nav className="flex gap-3 text-sm">
            <Link to="/" className="text-gray-600 hover:text-gray-900">
              홈
            </Link>
            <Link to="/search" className="text-gray-600 hover:text-gray-900">
              검색
            </Link>
            <Link to="/recommendation" className="text-gray-600 hover:text-gray-900">
              추천
            </Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl p-4 sm:p-6">
        <Outlet />
      </main>
    </div>
  )
}
