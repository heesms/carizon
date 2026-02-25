import React, { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Outlet, Link, NavLink, useNavigate, useLocation } from 'react-router-dom'

const PAGE_NAMES: Record<string, string> = {
  '/':               '',
  '/search':         '검색',
  '/recommendation': 'AI 중고차 매물 추천',
  '/ai-ranking':     'AI 중고차 매물 랭킹',
  '/likes':          '찜한 차량',
  '/info':           '정보',
}

function useScrollHide() {
  const [visible, setVisible] = useState(true)
  const lastY = useRef(0)
  useEffect(() => {
    const onScroll = () => {
      const y = window.scrollY
      if (y < 10) { setVisible(true) }
      else if (y > lastY.current + 4) { setVisible(false) }
      else if (y < lastY.current - 4) { setVisible(true) }
      lastY.current = y
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])
  return visible
}

export default function Layout() {
  const [q, setQ] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const headerVisible = useScrollHide()

  // 라우트 이동 시 기본은 상단으로 이동.
  // 단, 상세 -> 검색 복귀(스크롤 복원) 케이스만 예외 처리.
  useLayoutEffect(() => {
    const routeState = (location.state ?? {}) as { restoreSearchScrollFromDetail?: boolean }
    const preserveSearchScroll =
      location.pathname === '/search' && routeState.restoreSearchScrollFromDetail === true
    if (preserveSearchScroll) return

    const html = document.documentElement
    const prevInlineBehavior = html.style.scrollBehavior
    html.style.scrollBehavior = 'auto'
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' })
    requestAnimationFrame(() => {
      html.style.scrollBehavior = prevInlineBehavior
    })
  }, [location.pathname, location.search, location.hash, location.state])

  const pageTitle = (() => {
    const path = location.pathname
    if (path.startsWith('/cars/')) return '차량 상세'
    return PAGE_NAMES[path] ?? ''
  })()

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (!q.trim()) return
    navigate(`/search?q=${encodeURIComponent(q.trim())}`)
  }

  const navLinkCls = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-semibold transition-colors ${isActive ? 'text-brand-600' : 'text-gray-600 hover:text-gray-900'}`

  const bottomNavCls = ({ isActive }: { isActive: boolean }) =>
    `flex-1 flex flex-col items-center justify-center gap-0.5 py-2 transition-colors ${
      isActive ? 'text-brand-600' : 'text-gray-400 hover:text-gray-600'
    }`

  return (
    <div className="min-h-screen min-h-[100dvh] flex flex-col">

      {/* ── 모바일 헤더 (스크롤 시 사라짐) ── */}
      <header
        className="sm:hidden fixed top-0 left-0 right-0 z-50 bg-white border-b border-gray-100 shadow-sm transition-transform duration-200"
        style={{ transform: headerVisible ? 'translateY(0)' : 'translateY(-100%)' }}
      >
        <div className="flex items-center h-14 px-4 gap-3">
          {/* 로고 */}
          <Link to="/" className="flex items-center shrink-0">
            <img src="/carizon_logo.png" alt="Carizon" className="h-7" />
          </Link>
          {/* 현재 페이지명 */}
          {pageTitle && (
            <span className="flex-1 text-center text-sm font-bold text-gray-800 truncate pr-14">
              {pageTitle}
            </span>
          )}
        </div>
      </header>

      {/* ── 헤더 (데스크톱만) ── */}
      <header className="hidden sm:block sticky top-0 z-50 bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center h-14 sm:h-16 gap-3 sm:gap-4">
            {/* 로고 */}
            <Link to="/" className="flex items-center shrink-0">
              <img src="/carizon_logo.png" alt="Carizon" className="h-7 sm:h-8" />
            </Link>

            {/* 검색바 */}
            <form onSubmit={handleSearch} className="flex-1 max-w-md">
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

            {/* 네비 */}
            <nav className="flex items-center gap-1 ml-auto">
              <NavLink to="/search" className={navLinkCls}>검색</NavLink>
              <span className="w-px h-4 bg-gray-200 mx-2" />
              <NavLink to="/recommendation" className={navLinkCls}>Carizon AI</NavLink>
              <span className="w-px h-4 bg-gray-200 mx-2" />
              <NavLink to="/ai-ranking" className={navLinkCls}>AI 매물 랭킹</NavLink>
              <span className="w-px h-4 bg-gray-200 mx-2" />
              <NavLink to="/likes" className={navLinkCls}>찜한 차량</NavLink>
            </nav>
          </div>
        </div>
      </header>

      {/* ── 본문 ── */}
      <main className="flex-1 pt-14 pb-16 sm:pt-0 sm:pb-0">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 lg:py-8">
          <Outlet />
        </div>
      </main>

      {/* ── 푸터 (데스크톱만) ── */}
      <footer className="hidden sm:block bg-white border-t border-gray-100 mt-auto">
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

      {/* ── 모바일 하단 내비게이션 ── */}
      <nav className="sm:hidden fixed bottom-0 left-0 right-0 z-50 bg-white border-t border-gray-200"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
        <div className="flex items-stretch h-14">

          <NavLink to="/" end className={bottomNavCls}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
            <span className="text-[10px] font-medium">메인</span>
          </NavLink>

          <NavLink to="/search" className={bottomNavCls}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <span className="text-[10px] font-medium">검색</span>
          </NavLink>

          <NavLink to="/recommendation" className={bottomNavCls}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            <span className="text-[10px] font-medium">AI추천</span>
          </NavLink>

          <NavLink to="/ai-ranking" className={bottomNavCls}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
            <span className="text-[10px] font-medium">AI랭킹</span>
          </NavLink>

          <NavLink to="/likes" className={bottomNavCls}>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
            </svg>
            <span className="text-[10px] font-medium">찜</span>
          </NavLink>

        </div>
      </nav>
    </div>
  )
}
