import React, { useEffect, useState, useRef } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import AdSlot from '@/components/AdSlot'
import { getWeeklyBest } from '@/api/likes'
import { trackSearch, trackQuickSearch, trackAiRecommendation, trackAiPromptClick, trackSelectItem } from '@/lib/analytics'
import CarImagePlaceholder from '@/components/CarImagePlaceholder'
import { getMakerLogoUrl } from '@/api/seo'
import VehicleIcon from '@/components/VehicleIcon'

const AI_QUICK_PROMPTS = [
  '500만원 이하 경제적인 소형차 추천해줘',
  '아이 둘 있는 가족용 SUV 3000만원 이하',
  '출퇴근용 전기차, 주행거리 넉넉한 것',
  '20대 첫차로 좋은 중고차',
]

function WeeklyCardImage({ src, alt, className }: { src: string | null; alt: string; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) return <CarImagePlaceholder />
  return (
    <img
      src={src}
      alt={alt}
      className={className}
      loading="lazy"
      decoding="async"
      onError={() => setFailed(true)}
    />
  )
}

type WeeklyItem = {
  carId?: number
  makerName?: string
  modelName?: string
  year?: number
  mileage?: number
  price?: number
  fuel?: string
  region?: string
  carImageUrl?: string
  modelCode?: string
  carizonScore?: number
  rank?: number
  // 이전 API 호환
  maker?: string
  model?: string
  priceMin?: number
  priceMax?: number
  representativeImageUrl?: string
  _resolvedImageUrl?: string | null
}

const QUICK_SEARCHES = [
  { label: '현대', param: { makerCode: '101' } },
  { label: '기아',  param: { makerCode: '102' } },
  { label: 'BMW',   param: { makerCode: '107' } },
  { label: '벤츠',  param: { makerCode: '108' } },
  { label: 'SUV',   param: { bodyType: 'SUV' } },
  { label: '전기차', param: { fuel: '전기' } },
]

// 브랜드 전용관
const BRAND_GALLERY = [
  { makerCode: '101', slug: 'hyundai',       name: 'HYUNDAI' },
  { makerCode: '102', slug: 'kia',           name: 'KIA' },
  { makerCode: '142', slug: 'chevrolet',     name: 'CHEVROLET' },
  { makerCode: '189', slug: 'genesis',       name: 'GENESIS' },
  { makerCode: '104', slug: 'kg-mobility',   name: 'KG Mobility' },
  { makerCode: '105', slug: 'renault-korea', name: 'RENAULT' },
  { makerCode: '108', slug: 'mercedes-benz', name: 'Mercedes-Benz' },
  { makerCode: '107', slug: 'bmw',           name: 'BMW' },
  { makerCode: '109', slug: 'audi',          name: 'AUDI' },
  { makerCode: '112', slug: 'volkswagen',    name: 'Volkswagen' },
  { makerCode: '160', slug: 'mini',          name: 'MINI' },
  { makerCode: '114', slug: 'porsche',       name: 'Porsche' },
  { makerCode: '116', slug: 'land-rover',    name: 'Land Rover' },
  { makerCode: '190', slug: 'tesla',         name: 'Tesla' },
  { makerCode: '133', slug: 'lexus',         name: 'LEXUS' },
  { makerCode: '117', slug: 'volvo',         name: 'Volvo' },
]

const BODY_TYPE_GALLERY = [
  { slug: 'micro',    svgType: 'micro',    color: '#3B82F6', label: '경차' },
  { slug: 'small',    svgType: 'small',    color: '#FACC15', label: '소형' },
  { slug: 'compact',  svgType: 'compact',  color: '#EF4444', label: '준중형' },
  { slug: 'midsize',  svgType: 'midsize',  color: '#E5E7EB', label: '중형' },
  { slug: 'fullsize', svgType: 'fullsize', color: '#1F2937', label: '대형' },
  { slug: 'rv',       svgType: 'rv',       color: '#8B5CF6', label: 'RV' },
  { slug: 'suv',      svgType: 'suv',      color: '#10B981', label: 'SUV' },
  { slug: 'sports',   svgType: 'sports',   color: '#F97316', label: '스포츠카' },
  { slug: 'cargo',    svgType: 'cargo',    color: '#6B7280', label: '화물' },
]


function BrandLogo({ makerCode, name }: { makerCode: string; name: string }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    return (
      <div className="w-12 h-12 rounded-xl bg-gray-100 flex items-center justify-center text-gray-500 text-xs font-bold">
        {name.slice(0, 2)}
      </div>
    )
  }
  return (
    <img
      src={getMakerLogoUrl(makerCode)}
      alt={name}
      className="w-12 h-12 object-contain"
      onError={() => setFailed(true)}
    />
  )
}

const HISTORY_KEY = 'carizon_search_history'
const MAX_HISTORY = 5

function getSearchHistory(): string[] {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]') }
  catch { return [] }
}
function addSearchHistory(q: string) {
  const list = getSearchHistory().filter(h => h !== q)
  list.unshift(q)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list.slice(0, MAX_HISTORY)))
}
function removeSearchHistory(q: string) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(getSearchHistory().filter(h => h !== q)))
}

function resolveWeeklyImage(item: WeeklyItem) {
    const img = item._resolvedImageUrl || item.carImageUrl || item.representativeImageUrl || (item.modelCode ? `/image/car/model/${item.modelCode}.webp` : null)
    return img && img !== 'null' ? img : null
  }

  function checkImageUrl(url: string) {
    return new Promise<boolean>(resolve => {
      const img = new Image()
      let done = false
      const doneWith = (ok: boolean) => {
        if (done) return
        done = true
        resolve(ok)
      }
      const timer = window.setTimeout(() => doneWith(false), 3500)
      img.onload = () => {
        clearTimeout(timer)
        doneWith(true)
      }
      img.onerror = () => {
        clearTimeout(timer)
        doneWith(false)
      }
      img.src = url
    })
  }

export default function Home() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [weekly, setWeekly] = useState<WeeklyItem[]>([])
  const [history, setHistory] = useState<string[]>([])
  const [showHistory, setShowHistory] = useState(false)
  const [aiQuery, setAiQuery] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const historyRef = useRef<HTMLFormElement>(null)
  const location = useLocation()

  useEffect(() => {
    let active = true
    getWeeklyBest()
      .then(async (d: any) => {
        const source = Array.isArray(d) ? d : []
        const candidates: Array<{ item: WeeklyItem; imageUrl: string }> = source
          .map((item: any) => {
            const normalized = item as WeeklyItem
            const imageUrl = resolveWeeklyImage(normalized)
            return imageUrl ? { item: normalized, imageUrl } : null
          })
          .filter((x): x is { item: WeeklyItem; imageUrl: string } => !!x)
          .slice(0, 20)

        // 병렬로 이미지 유효성 체크
        const results = await Promise.allSettled(
          candidates.map(({ item, imageUrl }) =>
            checkImageUrl(imageUrl).then(ok => ok ? { item, imageUrl } : null)
          )
        )
        if (!active) return

        const weeklyItems = results
          .filter((r): r is PromiseFulfilledResult<{ item: WeeklyItem; imageUrl: string }> =>
            r.status === 'fulfilled' && r.value !== null
          )
          .slice(0, 12)
          .map(r => ({ ...r.value.item, _resolvedImageUrl: r.value.imageUrl }))

        if (active) setWeekly(weeklyItems)
      })
      .catch(() => {})
    setHistory(getSearchHistory())
    // 방문자 알림 (fire-and-forget)
    fetch('/api/analytics/visit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ page: '/', referer: document.referrer }),
    }).catch(() => {})
    return () => {
      active = false
    }
  }, [])

  // 히스토리 외부 클릭 닫기
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (historyRef.current && !historyRef.current.contains(e.target as Node)) setShowHistory(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = query.trim()
    if (q) { addSearchHistory(q); trackSearch(q) }
    setHistory(getSearchHistory())
    setShowHistory(false)
    navigate(q ? `/search?q=${encodeURIComponent(q)}` : '/search')
  }

  return (
    <div className="space-y-8 sm:space-y-10 animate-fade-in">

      {/* ── 히어로 ── */}
      <section className="relative overflow-hidden bg-gradient-to-br from-brand-600 via-brand-700 to-blue-800 rounded-2xl sm:rounded-3xl px-5 sm:px-8 py-10 sm:py-14 text-white text-center">
        {/* 배경 장식 */}
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-0 right-0 w-64 sm:w-96 h-64 sm:h-96 bg-white rounded-full -translate-y-1/2 translate-x-1/2" />
          <div className="absolute bottom-0 left-0 w-40 sm:w-64 h-40 sm:h-64 bg-white rounded-full translate-y-1/2 -translate-x-1/2" />
        </div>

        <div className="relative">
          <div className="inline-flex items-center gap-2 bg-white/15 backdrop-blur-sm border border-white/20 rounded-full px-3 sm:px-4 py-1 sm:py-1.5 text-xs sm:text-sm font-medium mb-4 sm:mb-6">
            <span className="w-2 h-2 bg-green-400 rounded-full animate-pulse" />
            중고차 플랫폼 매물 실시간 통합 수집 중
          </div>

          <h1 className="text-2xl sm:text-4xl lg:text-5xl font-black mb-2 sm:mb-3 leading-tight">
            중고차, 한 번에 비교하세요
          </h1>
          <p className="text-blue-100 text-sm sm:text-lg mb-6 sm:mb-8">
            중고차 플랫폼 매물을 통합해 최저가를 찾아드립니다
          </p>

          {/* 검색 폼 */}
          <form onSubmit={handleSearch} className="max-w-xl mx-auto relative" ref={historyRef}>
            <div className="flex flex-col sm:flex-row gap-2 p-1.5 bg-white/10 backdrop-blur-sm border border-white/20 rounded-xl sm:rounded-2xl">
              <input
                ref={inputRef}
                value={query}
                onChange={e => setQuery(e.target.value)}
                onFocus={() => history.length > 0 && setShowHistory(true)}
                placeholder="차량번호, 제조사, 모델명 입력"
                className="flex-1 bg-transparent px-4 py-2.5 text-white placeholder-blue-200 text-sm focus:outline-none"
              />
              <button type="submit"
                className="px-5 py-2.5 bg-white text-brand-700 font-bold rounded-lg sm:rounded-xl text-sm hover:bg-blue-50 transition-colors shadow-sm">
                검색
              </button>
            </div>

            {/* 검색 이력 드롭다운 */}
            {showHistory && history.length > 0 && (
              <div className="absolute left-0 right-0 top-full mt-1 bg-white/95 backdrop-blur-md rounded-xl shadow-lg border border-white/30 overflow-hidden z-10 text-left">
                <div className="flex items-center justify-between px-3 py-1.5 border-b border-gray-100">
                  <span className="text-[11px] font-medium text-gray-400">최근 검색</span>
                  <button type="button" onClick={() => { localStorage.removeItem(HISTORY_KEY); setHistory([]); setShowHistory(false) }}
                    className="text-[11px] text-gray-400 hover:text-gray-600">전체 삭제</button>
                </div>
                {history.map(h => (
                  <div key={h} className="flex items-center px-3 py-2 hover:bg-gray-50 transition-colors group">
                    <button type="button"
                      onClick={() => { setQuery(h); setShowHistory(false); navigate(`/search?q=${encodeURIComponent(h)}`) }}
                      className="flex-1 text-sm text-gray-700 text-left truncate">
                      {h}
                    </button>
                    <button type="button"
                      onClick={(e) => { e.stopPropagation(); removeSearchHistory(h); setHistory(getSearchHistory()) }}
                      className="ml-2 text-gray-300 hover:text-gray-500 opacity-0 group-hover:opacity-100 transition-opacity text-xs">
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </form>

          {/* 빠른 검색 */}
          <div className="flex flex-wrap justify-center gap-1.5 sm:gap-2 mt-3 sm:mt-4">
            {QUICK_SEARCHES.map(qs => (
              <button
                key={qs.label}
                onClick={() => { trackQuickSearch(qs.label); navigate(`/search?${new URLSearchParams(qs.param as any).toString()}`) }}
                className="px-2.5 sm:px-3 py-1 bg-white/15 hover:bg-white/25 border border-white/20 rounded-full text-xs sm:text-sm font-medium transition-colors"
              >
                {qs.label}
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* ── 광고 배너 (상단) ── */}
      <AdSlot id="home-top-banner" variant="banner" />

      {/* ── 위클리베스트 (중고차 한번에 비교하세요 바로 아래) ── */}
      {weekly.length > 0 && (
        <section>
          <div className="flex items-center justify-between mb-3 sm:mb-4">
            <div>
              <h2 className="section-title">실시간 인기 차량</h2>
              <p className="section-sub">실시간 주목받는 인기 매물</p>
            </div>
            <Link to="/search" className="btn-ghost text-brand-600 text-sm">전체보기 →</Link>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 sm:gap-4">
            {weekly.map((w, i) => {
              const v = (s?: string | null) => (s && s !== 'null') ? s : ''
              const name = [v(w.makerName) || v(w.maker), v(w.modelName) || v(w.model)].filter(Boolean).join(' ')
              const imgUrl = resolveWeeklyImage(w)
              const price = w.price ?? w.priceMin ?? w.priceMax
              return (
                <Link
                  key={w.carId ?? i}
                  to={w.carId ? `/cars/${w.carId}` : '/search'}
                  state={w.carId ? {
                    from: `${location.pathname}${location.search}${location.hash}`,
                    source: 'other',
                    backgroundLocation: location,
                  } : undefined}
                  className="card-hover overflow-hidden group"
                  onClick={() => w.carId && trackSelectItem(w.carId, v(w.makerName) || v(w.maker), v(w.modelName) || v(w.model), 'weekly_best')}
                >
                  {/* 이미지 */}
                  <div className="relative aspect-[16/9] bg-gray-100">
                    <WeeklyCardImage
                      src={imgUrl}
                      alt={name}
                      className="w-full h-full object-cover object-[center_65%] [@media(hover:hover)]:transition-transform [@media(hover:hover)]:duration-300 [@media(hover:hover)]:group-hover:scale-105"
                    />
                    <span className="absolute top-2 left-2 w-7 h-7 rounded-full bg-brand-600 text-white text-xs font-black flex items-center justify-center shadow">
                      {i + 1}
                    </span>
                    {!!w.carizonScore && (
                      <span className="absolute top-2 right-2 bg-white/90 backdrop-blur-sm text-brand-600 text-[10px] font-bold px-1.5 py-0.5 rounded-full shadow-sm">
                        {Math.round(Number(w.carizonScore))}점
                      </span>
                    )}
                  </div>
                  {/* 정보 */}
                  <div className="p-3 sm:p-4">
                    <div className="font-bold text-sm text-gray-900 truncate group-hover:text-brand-600 transition-colors mb-1">
                      {name.trim()}
                    </div>
                    <div className="flex flex-wrap gap-1 mb-2">
                      {!!w.year && <span className="badge badge-gray text-[10px]">{w.year}년</span>}
                      {!!w.mileage && <span className="badge badge-gray text-[10px]">{w.mileage.toLocaleString()}km</span>}
                      {!!v(w.fuel) && <span className="badge badge-blue text-[10px]">{w.fuel}</span>}
                      {!!v(w.region) && <span className="badge badge-gray text-[10px]">{w.region}</span>}
                    </div>
                    {!!price && (
                      <div className="text-base font-black text-brand-600">
                        {price.toLocaleString()}만원
                      </div>
                    )}
                  </div>
                </Link>
              )
            })}
          </div>
        </section>
      )}

      {/* ── AI 자연어 추천 ── */}
      <section className="card overflow-hidden">
        <div className="bg-gradient-to-r from-violet-600 to-purple-700 px-5 sm:px-6 py-5 text-white">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-lg">✨</span>
            <span className="font-black text-base sm:text-lg">AI에게 직접 물어보세요</span>
          </div>
          <p className="text-purple-100 text-xs sm:text-sm">원하는 조건을 자유롭게 입력하면 AI가 맞춤 매물을 추천해 드려요</p>
        </div>
        <div className="p-4 sm:p-5">
          <div className="flex flex-wrap gap-2 mb-3">
            {AI_QUICK_PROMPTS.map(p => (
              <button
                key={p}
                onClick={() => { trackAiPromptClick(p); navigate(`/recommendation?query=${encodeURIComponent(p)}`) }}
                className="text-xs px-3 py-1.5 bg-violet-50 hover:bg-violet-100 text-violet-700 rounded-full border border-violet-200 transition-colors font-medium"
              >
                {p}
              </button>
            ))}
          </div>
          <form
            onSubmit={e => {
              e.preventDefault()
              const q = aiQuery.trim()
              if (!q) return
              trackAiRecommendation(q, 'input')
              navigate(`/recommendation?query=${encodeURIComponent(q)}`)
            }}
            className="flex gap-2"
          >
            <input
              value={aiQuery}
              onChange={e => setAiQuery(e.target.value)}
              placeholder="예: 3000만원 이하 가족용 SUV 추천해줘"
              className="input flex-1 text-sm"
            />
            <button
              type="submit"
              disabled={!aiQuery.trim()}
              className="px-4 py-2 bg-violet-600 text-white font-bold rounded-xl text-sm hover:bg-violet-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
            >
              AI 추천
            </button>
          </form>
        </div>
      </section>

      {/* ── 핵심 기능 3가지 ── */}
      <section>
        <h2 className="section-title mb-1">핵심 기능 3가지</h2>
        <p className="section-sub mb-4 sm:mb-6">찾고 싶은 기능을 바로 사용해보세요</p>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 sm:gap-5">

          {/* 1. 통합 검색 */}
          <div className="card overflow-hidden flex flex-col hover:shadow-md transition-shadow">
            <div className="bg-gradient-to-br from-brand-600 to-blue-700 px-5 py-6 text-white">
              <div className="flex items-center gap-2 mb-3">
                <span className="w-7 h-7 bg-white/20 rounded-lg flex items-center justify-center text-base">🔍</span>
                <span className="text-[11px] font-bold bg-white/20 px-2 py-0.5 rounded-full">기능 1</span>
              </div>
              <h3 className="font-black text-lg leading-tight">통합 검색</h3>
              <p className="text-blue-100 text-xs mt-1">중고차 플랫폼 매물을 한 번에</p>
            </div>
            <div className="p-4 flex-1 flex flex-col">
              <ul className="space-y-2 mb-4 flex-1">
                {[
                  '차량번호 기준 통합 · 중복 제거',
                  '실시간 플랫폼별 가격 비교',
                  '연식 · 가격 · 연료 · 차종 필터',
                ].map(t => (
                  <li key={t} className="flex items-start gap-1.5 text-xs text-gray-600">
                    <span className="text-brand-500 font-bold mt-0.5 shrink-0">✓</span>{t}
                  </li>
                ))}
              </ul>
              <Link to="/search"
                className="block text-center text-sm font-bold py-2.5 rounded-xl bg-brand-600 text-white hover:bg-brand-700 transition-colors">
                검색하기 →
              </Link>
            </div>
          </div>

          {/* 2. Carizon AI */}
          <div className="card overflow-hidden flex flex-col hover:shadow-md transition-shadow">
            <div className="bg-gradient-to-br from-violet-600 to-purple-700 px-5 py-6 text-white">
              <div className="flex items-center gap-2 mb-3">
                <span className="w-7 h-7 bg-white/20 rounded-lg flex items-center justify-center text-base">✨</span>
                <span className="text-[11px] font-bold bg-white/20 px-2 py-0.5 rounded-full">기능 2 · AI</span>
              </div>
              <h3 className="font-black text-lg leading-tight">Carizon AI</h3>
              <p className="text-purple-100 text-xs mt-1">조건을 말하면 AI가 찾아드립니다</p>
            </div>
            <div className="p-4 flex-1 flex flex-col">
              <ul className="space-y-2 mb-4 flex-1">
                {[
                  'LLM이 요청을 분석해 매물 추천',
                  '예산 · 용도 · 차종 자유롭게 입력',
                  '추천 이유를 한 줄씩 설명',
                ].map(t => (
                  <li key={t} className="flex items-start gap-1.5 text-xs text-gray-600">
                    <span className="text-violet-500 font-bold mt-0.5 shrink-0">✓</span>{t}
                  </li>
                ))}
              </ul>
              <Link to="/recommendation"
                className="block text-center text-sm font-bold py-2.5 rounded-xl bg-violet-600 text-white hover:bg-violet-700 transition-colors">
                AI에게 물어보기 →
              </Link>
            </div>
          </div>

          {/* 3. AI 매물 랭킹 */}
          <div className="card overflow-hidden flex flex-col hover:shadow-md transition-shadow">
            <div className="bg-gradient-to-br from-amber-500 to-orange-600 px-5 py-6 text-white">
              <div className="flex items-center gap-2 mb-3">
                <span className="w-7 h-7 bg-white/20 rounded-lg flex items-center justify-center text-base">🏆</span>
                <span className="text-[11px] font-bold bg-white/20 px-2 py-0.5 rounded-full">기능 3 · AI</span>
              </div>
              <h3 className="font-black text-lg leading-tight">AI 매물 랭킹</h3>
              <p className="text-amber-100 text-xs mt-1">모델별 최고의 매물을 AI가 선별</p>
            </div>
            <div className="p-4 flex-1 flex flex-col">
              <ul className="space-y-2 mb-4 flex-1">
                {[
                  '카리즌 스코어로 객관적 평가',
                  '가격 · 주행거리 · 연식 종합 분석',
                  '원하는 모델 TOP 10 자동 선별',
                ].map(t => (
                  <li key={t} className="flex items-start gap-1.5 text-xs text-gray-600">
                    <span className="text-amber-500 font-bold mt-0.5 shrink-0">✓</span>{t}
                  </li>
                ))}
              </ul>
              <Link to="/ai-ranking"
                className="block text-center text-sm font-bold py-2.5 rounded-xl bg-amber-500 text-white hover:bg-amber-600 transition-colors">
                랭킹 보기 →
              </Link>
            </div>
          </div>

        </div>
      </section>

      {/* ── 브랜드 전용관 ── */}
      <section>
        <div className="flex items-center justify-between mb-3 sm:mb-4">
          <div>
            <h2 className="section-title">브랜드 전용관</h2>
            <p className="section-sub">브랜드별 중고차 매물을 한눈에</p>
          </div>
        </div>
        <div className="grid grid-cols-4 sm:grid-cols-8 gap-2 sm:gap-3">
          {BRAND_GALLERY.map(b => (
            <Link
              key={b.makerCode}
              to={`/cars/maker/${b.slug}`}
              className="flex flex-col items-center gap-1.5 p-3 rounded-xl bg-white border border-gray-100 hover:border-blue-300 hover:shadow-sm transition-all group"
            >
              <BrandLogo makerCode={b.makerCode} name={b.name} />
              <span className="text-xs text-gray-600 group-hover:text-blue-600 font-medium transition-colors text-center leading-tight">
                {b.name}
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* ── 차종별 전용관 ── */}
      <section>
        <div className="flex items-center justify-between mb-3 sm:mb-4">
          <div>
            <h2 className="section-title">차종별 전용관</h2>
            <p className="section-sub">원하는 차종의 중고차를 바로 확인</p>
          </div>
        </div>
        <div className="grid grid-cols-3 lg:grid-cols-9 gap-2 sm:gap-3">
          {BODY_TYPE_GALLERY.map(b => (
            <Link
              key={b.slug}
              to={`/cars/type/${b.slug}`}
              className="flex flex-col items-center gap-1.5 p-3 rounded-xl bg-white border border-gray-100 hover:border-green-300 hover:shadow-sm transition-all group"
            >
              <VehicleIcon type={b.svgType} color={b.color} />
              <span className="text-xs font-semibold text-gray-700 group-hover:text-green-700 transition-colors text-center leading-tight">
                {b.label}
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* ── 광고 배너 (하단) ── */}
      <AdSlot id="home-bottom-banner" variant="rectangle" className="max-w-sm" />
    </div>
  )
}
