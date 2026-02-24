import type { MouseEvent as ReactMouseEvent, TouchEvent as ReactTouchEvent } from 'react'
import { StrictMode, useCallback, useEffect, useRef } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, useLocation, useNavigate, type Location } from 'react-router-dom'
import './main.css'

import Layout from './components/Layout'
import Home from './pages/Home'
import Search from './pages/Search'
import CarDetail from './pages/CarDetail'
import Recommendation from './pages/Recommendation'
import MyLikes from './pages/MyLikes'
import AiRankingBest from './pages/AiRankingBest'
import Info from './pages/Info'
import { applyRouteSeo } from './utils/seo'
import { initGoogleAnalytics, trackPageView } from './utils/analytics'

type AppLocationState = { backgroundLocation?: Location }

function AppRoutes() {
  const location = useLocation()
  const state = (location.state ?? {}) as AppLocationState
  const backgroundLocation = state.backgroundLocation

  useEffect(() => {
    initGoogleAnalytics()
  }, [])

  useEffect(() => {
    const fullPath = `${location.pathname}${location.search}`
    const seoMeta = applyRouteSeo(location.pathname, location.search)
    trackPageView(fullPath, seoMeta.title)
  }, [location.pathname, location.search])

  return (
    <>
      <Routes location={backgroundLocation || location}>
        <Route path="/" element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="search" element={<Search />} />
          <Route path="likes" element={<MyLikes />} />
          <Route path="cars/:id" element={<CarDetail />} />
          <Route path="recommendation" element={<Recommendation />} />
          <Route path="ai-ranking" element={<AiRankingBest />} />
          <Route path="info" element={<Info />} />
        </Route>
      </Routes>

      {backgroundLocation ? <DetailModal /> : null}
    </>
  )
}

/** 바텀시트 스타일의 차량 상세 모달 (스와이프 다운으로 닫기 지원) */
function DetailModal() {
  const navigate = useNavigate()
  const sheetRef = useRef<HTMLDivElement>(null)
  const dragStartY = useRef(0)
  const dragDeltaY = useRef(0)
  const isDragging = useRef(false)
  const DISMISS_THRESHOLD = 90

  // 바디 스크롤 잠금
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [])

  const dismiss = useCallback(() => {
    const el = sheetRef.current
    const isMobile = window.innerWidth < 640
    if (el && isMobile) {
      el.style.transition = 'transform 0.28s cubic-bezier(0.32,0.72,0,1)'
      el.style.transform = 'translateY(100%)'
      setTimeout(() => navigate(-1), 260)
    } else {
      navigate(-1)
    }
  }, [navigate])

  // 터치 이벤트 (모바일)
  const onTouchStart = useCallback((e: ReactTouchEvent<HTMLDivElement>) => {
    dragStartY.current = e.touches[0].clientY
    isDragging.current = true
    dragDeltaY.current = 0
    if (sheetRef.current) {
      sheetRef.current.style.animation = 'none'
      sheetRef.current.style.transition = 'none'
    }
  }, [])

  const onTouchMove = useCallback((e: ReactTouchEvent<HTMLDivElement>) => {
    if (!isDragging.current) return
    const delta = Math.max(0, e.touches[0].clientY - dragStartY.current)
    dragDeltaY.current = delta
    if (sheetRef.current) sheetRef.current.style.transform = `translateY(${delta}px)`
  }, [])

  const onTouchEnd = useCallback(() => {
    isDragging.current = false
    if (dragDeltaY.current > DISMISS_THRESHOLD) {
      dismiss()
    } else {
      if (sheetRef.current) {
        sheetRef.current.style.transition = 'transform 0.25s cubic-bezier(0.32,0.72,0,1)'
        sheetRef.current.style.transform = 'translateY(0)'
      }
    }
    dragDeltaY.current = 0
  }, [dismiss])

  // 마우스 드래그 (데스크탑 테스트용)
  const onMouseMove = useCallback((e: globalThis.MouseEvent) => {
    if (!isDragging.current) return
    const delta = Math.max(0, e.clientY - dragStartY.current)
    dragDeltaY.current = delta
    if (sheetRef.current) sheetRef.current.style.transform = `translateY(${delta}px)`
  }, [])

  const onMouseUp = useCallback((e: globalThis.MouseEvent) => {
    if (!isDragging.current) return
    isDragging.current = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
    if (dragDeltaY.current > DISMISS_THRESHOLD) {
      dismiss()
    } else {
      if (sheetRef.current) {
        sheetRef.current.style.transition = 'transform 0.25s cubic-bezier(0.32,0.72,0,1)'
        sheetRef.current.style.transform = 'translateY(0)'
      }
    }
    dragDeltaY.current = 0
  }, [dismiss, onMouseMove])

  const onMouseDown = useCallback((e: ReactMouseEvent<HTMLDivElement>) => {
    dragStartY.current = e.clientY
    isDragging.current = true
    dragDeltaY.current = 0
    if (sheetRef.current) {
      sheetRef.current.style.animation = 'none'
      sheetRef.current.style.transition = 'none'
    }
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }, [onMouseMove, onMouseUp])

  useEffect(() => {
    return () => {
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
  }, [onMouseMove, onMouseUp])

  return (
    <Routes>
      <Route
        path="/cars/:id"
        element={
          <div className="fixed inset-0 z-50 flex flex-col justify-end sm:items-center sm:justify-center sm:px-6 sm:py-6 lg:px-10 lg:py-8">
            {/* 반투명 배경 */}
            <div
              className="absolute inset-0 bg-black/40 backdrop-blur-[3px] animate-fade-in"
              onClick={dismiss}
            />

            {/* 모바일: 바텀시트 / PC: 중앙 다이얼로그 */}
            <div
              ref={sheetRef}
              className="relative bg-white
                rounded-t-[32px] sm:rounded-tl-[28px] sm:rounded-tr-[28px] sm:rounded-bl-[28px] sm:rounded-br-[28px]
                overflow-hidden flex flex-col w-full sm:w-[min(96vw,72rem)]
                animate-sheet-up sm:animate-dialog-in"
              style={{
                maxHeight: '94dvh',
                boxShadow: '0 -8px 40px rgba(0,0,0,0.18), 0 0 0 1px rgba(0,0,0,0.04)',
              }}
              onClick={e => e.stopPropagation()}
            >
              {/* 드래그 핸들 (모바일 전용) */}
              <div
                className="sm:hidden shrink-0 flex items-center justify-center pt-3.5 pb-2.5 cursor-grab active:cursor-grabbing touch-none select-none"
                onTouchStart={onTouchStart}
                onTouchMove={onTouchMove}
                onTouchEnd={onTouchEnd}
                onMouseDown={onMouseDown}
              >
                <div className="w-9 h-[5px] bg-gray-200 rounded-full" />
              </div>

              {/* 스크롤 콘텐츠 */}
              <div className="flex-1 min-h-0 sm:p-1.5">
                <div className="h-full min-h-0 overflow-y-auto overscroll-contain scrollbar-thin [scrollbar-gutter:stable]">
                  <CarDetail onClose={dismiss} />
                </div>
              </div>
            </div>
          </div>
        }
      />
    </Routes>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  </StrictMode>
)
