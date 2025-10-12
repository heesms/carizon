import React from 'react'
import { Outlet, Link, useLocation } from 'react-router-dom'

export default function App(){
  const loc = useLocation()
  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-white border-b">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center gap-6">
          <Link to="/" className="font-bold text-xl">Carizon</Link>
          <nav className="text-sm text-gray-600">
            <Link to="/search" className={loc.pathname.startsWith('/search') ? 'font-semibold' : ''}>검색</Link>
          </nav>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t bg-white">
        <div className="max-w-6xl mx-auto px-4 py-6 text-xs text-gray-500">
          © Carizon
        </div>
      </footer>
    </div>
  )
}
