import React from 'react'
import { Link, useLocation } from 'react-router-dom'

const Header: React.FC = () => {
    const loc = useLocation()
    return (
        <header className="bg-white border-b sticky top-0 z-40">
            <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between gap-6">
                <Link to="/" className="flex items-center gap-2">
                    <img
                        src="/image/logo/carizon_logo.png"
                        alt="Carizon"
                        className="h-6 w-auto md:h-7"   // ← 기존 h-8 → h-6 로 축소
                    />
                    <span className="sr-only">Carizon</span>
                </Link>
                <nav className="text-sm text-gray-700 flex items-center gap-4">
                    <Link to="/" className={loc.pathname==='/'?'font-semibold':''}>홈</Link>
                    <Link to="/search" className={loc.pathname.startsWith('/search')?'font-semibold':''}>검색</Link>
                </nav>
            </div>
        </header>
    )
}

export default Header
