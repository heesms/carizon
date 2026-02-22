import React, { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

export default function Header() {
    const loc = useLocation()
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
    
    const isActive = (path: string) => {
        if (path === '/') return loc.pathname === '/'
        return loc.pathname.startsWith(path)
    }

    return (
        <header className="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-gray-200 shadow-sm">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center justify-between h-16 lg:h-20">
                    {/* 로고 */}
                    <Link 
                        to="/" 
                        className="flex items-center gap-3 group"
                    >
                        <img
                            src="/image/logo/carizon_logo.png"
                            alt="Carizon"
                            className="h-8 w-auto lg:h-10 transition-transform group-hover:scale-105"
                        />
                        <span className="text-xl lg:text-2xl font-bold bg-gradient-to-r from-gray-900 to-gray-700 bg-clip-text text-transparent">
                            Carizon
                        </span>
                    </Link>

                    {/* 데스크톱 네비게이션 */}
                    <nav className="hidden md:flex items-center gap-1">
                        <NavLink to="/" isActive={isActive('/')} label="홈" />
                        <NavLink to="/search" isActive={isActive('/search')} label="검색" />
                        <NavLink to="/recommendation" isActive={isActive('/recommendation')} label="AI 추천" />
                    </nav>

                    {/* 모바일 메뉴 버튼 */}
                    <button
                        onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                        className="md:hidden p-2 rounded-lg hover:bg-gray-100 transition"
                        aria-label="메뉴"
                    >
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            {mobileMenuOpen ? (
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            ) : (
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                            )}
                        </svg>
                    </button>
                </div>

                {/* 모바일 메뉴 */}
                {mobileMenuOpen && (
                    <nav className="md:hidden pb-4 space-y-2 animate-fade-in">
                        <MobileNavLink to="/" isActive={isActive('/')} label="홈" onClick={() => setMobileMenuOpen(false)} />
                        <MobileNavLink to="/search" isActive={isActive('/search')} label="검색" onClick={() => setMobileMenuOpen(false)} />
                        <MobileNavLink to="/recommendation" isActive={isActive('/recommendation')} label="AI 추천" onClick={() => setMobileMenuOpen(false)} />
                    </nav>
                )}
            </div>
        </header>
    )
}

function NavLink({ to, isActive, label }: { to: string; isActive: boolean; label: string }) {
    return (
        <Link
            to={to}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                isActive
                    ? 'bg-black text-white shadow-md'
                    : 'text-gray-700 hover:bg-gray-100 hover:text-gray-900'
            }`}
        >
            {label}
        </Link>
    )
}

function MobileNavLink({ to, isActive, label, onClick }: { to: string; isActive: boolean; label: string; onClick: () => void }) {
    return (
        <Link
            to={to}
            onClick={onClick}
            className={`block px-4 py-3 rounded-lg text-base font-medium transition ${
                isActive
                    ? 'bg-black text-white'
                    : 'text-gray-700 hover:bg-gray-100'
            }`}
        >
            {label}
        </Link>
    )
}
