import React from 'react'
import { Outlet, Link, NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'

export default function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [q, setQ] = useState('')
  const navigate = useNavigate()

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (!q.trim()) return
    navigate(`/search?q=${encodeURIComponent(q.trim())}`)
    setMenuOpen(false)
  }

  const navLinkCls = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-semibold transition-colors ${isActive ? 'text-brand-600' : 'text-gray-600 hover:text-gray-900'}`

  return (
    <div className="min-h-screen min-h-[100dvh] flex flex-col">
      {/* ── 헤더 ── */}
      <header className="sticky top-0 z-50 bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center h-14 sm:h-16 gap-3 sm:gap-4">
            {/* 로고 */}
            <Link to="/" className="flex items-center shrink-0">
              <img src="/logo.png" alt="Carizon" className="h-7 sm:h-8" />
            </Link>

            {/* 검색바 (데스크톱) */}
            <form onSubmit={handleSearch} className="flex-1 max-w-md hidden sm:block">
              <div className="relative">
                <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input
                  value={q}
                  onChange={e => setQ(e.target.value)}
                  placeholder="차량번호, 제조사, 모델 검색..."
                  className="input pl-9 pr-4 py-2 text-sm"
                />
              </div>
            </form>

            {/* 네비 (데스크톱) */}
            <nav className="hidden sm:flex items-center gap-1 ml-auto">
              <NavLink to="/search" className={navLinkCls}>검색</NavLink>
              <span className="w-px h-4 bg-gray-200 mx-2" />
              <NavLink to="/recommendation" className={navLinkCls}>Carizon AI</NavLink>
              <span className="w-px h-4 bg-gray-200 mx-2" />
              <NavLink to="/ai-ranking" className={navLinkCls}>AI 매물 랭킹</NavLink>
              <span className="w-px h-4 bg-gray-200 mx-2" />
              <NavLink to="/likes" className={navLinkCls}>찜한 차량</NavLink>
            </nav>

            {/* 모바일: 검색 버튼 + 메뉴 버튼 */}
            <div className="sm:hidden flex items-center gap-1 ml-auto">
              <button
                className="p-2 rounded-lg hover:bg-gray-100 text-gray-600"
                onClick={() => { setMenuOpen(v => !v) }}
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  {menuOpen
                    ? <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    : <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />}
                </svg>
              </button>
            </div>
          </div>
        </div>

        {/* 모바일 드롭다운 */}
        {menuOpen && (
          <div className="sm:hidden border-t border-gray-100 bg-white px-4 py-3 space-y-3 animate-slide-up">
            <form onSubmit={handleSearch}>
              <div className="relative">
                <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input
                  value={q}
                  onChange={e => setQ(e.target.value)}
                  placeholder="차량번호, 제조사, 모델 검색..."
                  className="input pl-9 text-sm"
                />
              </div>
            </form>
            <div className="flex gap-4">
              <NavLink to="/search" className={navLinkCls} onClick={() => setMenuOpen(false)}>검색</NavLink>
              <NavLink to="/recommendation" className={navLinkCls} onClick={() => setMenuOpen(false)}>Carizon AI</NavLink>
              <NavLink to="/ai-ranking" className={navLinkCls} onClick={() => setMenuOpen(false)}>AI 매물 랭킹</NavLink>
              <NavLink to="/likes" className={navLinkCls} onClick={() => setMenuOpen(false)}>찜한 차량</NavLink>
            </div>
          </div>
        )}
      </header>

      {/* ── 본문 ── */}
      <main className="flex-1">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 lg:py-8">
          <Outlet />
        </div>
      </main>

      {/* ── 푸터 ── */}
      <footer className="bg-white border-t border-gray-100 mt-auto">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <span className="w-6 h-6 bg-brand-600 rounded flex items-center justify-center text-white font-black text-xs">C</span>
                <span className="font-black text-gray-900">Carizon</span>
              </div>
              <p className="text-xs text-gray-400">
                여러 중고차 플랫폼 매물을 한곳에서 통합 비교
              </p>
            </div>
            <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-gray-400">
              <Link to="/info?tab=contact" className="hover:text-gray-600 transition">문의하기</Link>
              <Link to="/info?tab=terms"   className="hover:text-gray-600 transition">이용약관</Link>
              <Link to="/info?tab=privacy" className="hover:text-gray-600 transition">개인정보처리방침</Link>
              <span>© 2026 Carizon</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
