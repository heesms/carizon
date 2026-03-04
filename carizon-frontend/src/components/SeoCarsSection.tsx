import React, { useCallback, useEffect, useRef, useState, useMemo } from 'react'
import { useSearchParams, useLocation } from 'react-router-dom'
import FiltersPanel from '@/components/FiltersPanel'
import CarCard from '@/components/CarCard'
import AdSlot from '@/components/AdSlot'
import { searchCars, type CarListItem } from '@/api/cars'
import { getMyLikes } from '@/api/likes'
import { trackFilterApply } from '@/lib/analytics'

const SIMPLE_SORTS = [
  { value: '', label: '추천순' },
  { value: 'RECENT', label: '최신순' },
] as const

const TOGGLE_SORTS = [
  { a: 'LOW_PRICE',  b: 'HIGH_PRICE', labelA: '가격 낮은순', labelB: '가격 높은순' },
  { a: 'LOW_KM',    b: 'HIGH_KM',    labelA: '주행 적은순', labelB: '주행 많은순' },
  { a: 'NEW_YEAR',  b: 'OLD_YEAR',   labelA: '신형순',       labelB: '구형순' },
] as const

function CarCardSkeleton({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return (
      <div className="card overflow-hidden flex flex-row animate-pulse">
        <div className="w-24 h-24 shrink-0 bg-gray-200" />
        <div className="p-2.5 flex flex-col gap-2 flex-1 justify-center">
          <div className="h-4 bg-gray-200 rounded w-3/4" />
          <div className="h-3 bg-gray-200 rounded w-1/2" />
          <div className="h-5 bg-gray-200 rounded w-2/5" />
        </div>
      </div>
    )
  }
  return (
    <div className="card overflow-hidden flex flex-col animate-pulse">
      <div className="w-full aspect-[3/2] bg-gray-200" />
      <div className="p-3 flex flex-col gap-2 flex-1">
        <div className="h-4 bg-gray-200 rounded w-3/4" />
        <div className="flex gap-1 flex-wrap">
          <div className="h-5 bg-gray-200 rounded w-12" />
          <div className="h-5 bg-gray-200 rounded w-16" />
          <div className="h-5 bg-gray-200 rounded w-10" />
        </div>
        <div className="mt-auto h-6 bg-gray-200 rounded w-2/5" />
      </div>
    </div>
  )
}

function buildActiveChips(params: Record<string, string>, fixedKeys: string[]) {
  const chips: Array<{ key: string; label: string; removeKeys: string[] }> = []

  // Skip fixed keys - they appear in the banner, not as removable chips
  if (params.q)
    chips.push({ key: 'q', label: `🔍 "${params.q}"`, removeKeys: ['q'] })

  if (!fixedKeys.includes('makerCode') && !fixedKeys.includes('modelCode')) {
    if (params.makerCode)
      chips.push({ key: 'maker', label: '🚗 제조사', removeKeys: ['makerCode', 'modelGroupCode', 'modelCode', 'trimCode'] })
    else if (params.modelCode)
      chips.push({ key: 'model', label: '🚗 모델', removeKeys: ['modelCode', 'trimCode'] })
  }

  if (params.priceMin || params.priceMax) {
    const min = params.priceMin ? `${Number(params.priceMin).toLocaleString()}만원` : ''
    const max = params.priceMax ? `${Number(params.priceMax).toLocaleString()}만원` : ''
    const label = min && max ? `${min}~${max}` : min ? `${min} 이상` : `${max} 이하`
    chips.push({ key: 'price', label: `💰 ${label}`, removeKeys: ['priceMin', 'priceMax'] })
  }

  if (params.yearMin || params.yearMax) {
    const min = params.yearMin ?? ''
    const max = params.yearMax ?? ''
    const label = min && max ? `${min}~${max}년식` : min ? `${min}년 이후` : `${max}년 이전`
    chips.push({ key: 'year', label: `📅 ${label}`, removeKeys: ['yearMin', 'yearMax'] })
  }

  if (params.kmMax)
    chips.push({ key: 'km', label: `🛣️ ${Number(params.kmMax).toLocaleString()}km 이하`, removeKeys: ['kmMin', 'kmMax'] })

  if (params.noAccident)
    chips.push({ key: 'noAccident', label: '✅ 무사고', removeKeys: ['noAccident'] })

  if (params.fuel)
    chips.push({ key: 'fuel', label: '⛽ 연료', removeKeys: ['fuel'] })

  if (!fixedKeys.includes('bodyType') && params.bodyType)
    chips.push({ key: 'bodyType', label: '🚙 차체', removeKeys: ['bodyType'] })

  if (params.color)
    chips.push({ key: 'color', label: '🎨 색상', removeKeys: ['color'] })

  return chips
}

type Props = {
  fixedParams: Record<string, string>
}

export default function SeoCarsSection({ fixedParams }: Props) {
  const [sp, setSp] = useSearchParams()
  const [items, setItems] = useState<CarListItem[]>([])
  const [likedCarIds, setLikedCarIds] = useState<Set<number>>(() => new Set())
  const [totalElements, setTotal] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [viewMode, setViewMode] = useState<'card' | 'list'>(() =>
    (localStorage.getItem('search_view_mode') as 'card' | 'list') ?? 'card'
  )
  const busyRef = useRef(false)
  const nextPageRef = useRef(0)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const fixedKeys = useMemo(() => Object.keys(fixedParams), [fixedParams])
  const urlParams = useMemo(() => Object.fromEntries(sp.entries()), [sp])
  // Merged params for API call: fixedParams always win
  const mergedParams = useMemo(() => ({ ...urlParams, ...fixedParams }), [urlParams, fixedParams])
  const currentSort = sp.get('sort') ?? ''

  useEffect(() => {
    let mounted = true
    getMyLikes(500)
      .then(data => {
        if (!mounted) return
        const ids = Array.isArray(data?.carIds) ? data.carIds : []
        setLikedCarIds(new Set(ids.map(Number).filter(Number.isFinite)))
      })
      .catch(() => {})
    return () => { mounted = false }
  }, [])

  const loadPage = useCallback(async (pageNum: number, reset: boolean) => {
    if (busyRef.current) return
    busyRef.current = true
    setLoading(true)
    try {
      const res = await searchCars({ ...mergedParams, page: pageNum, size: 30 })
      const content = res?.content ?? []
      if (reset) setItems(content)
      else setItems(prev => [...prev, ...content])
      setHasMore(pageNum + 1 < (res?.totalPages ?? 0))
      nextPageRef.current = pageNum + 1
      setTotal(res?.totalElements ?? 0)
    } catch {
      if (reset) setItems([])
    } finally {
      setLoading(false)
      busyRef.current = false
    }
  }, [JSON.stringify(mergedParams)]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    setItems([])
    setHasMore(false)
    nextPageRef.current = 0
    loadPage(0, true)
  }, [loadPage]) // eslint-disable-line react-hooks/exhaustive-deps

  // 무한 스크롤
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && hasMore && !busyRef.current) {
          loadPage(nextPageRef.current, false)
        }
      },
      { rootMargin: '800px' }
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, loadPage])

  const filteredItems = items.filter(item => {
    const min = item.priceMin
    const max = item.priceMax
    return (typeof min === 'number' && min >= 1) || (typeof max === 'number' && max >= 1)
  })

  // onChange: never touch fixedParam keys
  const setFilters = (v: Record<string, any>) => {
    trackFilterApply(v)
    const usp = new URLSearchParams()
    Object.entries(v).forEach(([k, val]) => {
      if (fixedKeys.includes(k)) return // protect fixed params
      if (val !== undefined && val !== null && val !== '') usp.set(k, String(val))
    })
    setSp(usp)
  }

  const setSort = (sort: string) => {
    const usp = new URLSearchParams(sp)
    if (sort) usp.set('sort', sort)
    else usp.delete('sort')
    setSp(usp)
  }

  const removeChip = (removeKeys: string[]) => {
    const usp = new URLSearchParams(sp)
    removeKeys.forEach(k => usp.delete(k))
    setSp(usp)
  }

  const clearAllFilters = () => {
    // Only clear non-fixed params
    setSp(new URLSearchParams())
  }

  const activeChips = useMemo(() => buildActiveChips({ ...urlParams, ...fixedParams }, fixedKeys), [urlParams, fixedParams, fixedKeys])
  // Only show removable chips (not from fixedKeys)
  const removableChips = activeChips.filter(c => !c.removeKeys.every(k => fixedKeys.includes(k)))

  const compact = viewMode === 'list'

  // Value for FiltersPanel: merged params (shows current state including fixed)
  const filterValue = useMemo(() => ({ ...urlParams, ...fixedParams }), [urlParams, fixedParams])

  return (
    <div className="space-y-5">
      {/* FiltersPanel - same as Search */}
      <FiltersPanel value={filterValue} onChange={setFilters} onSearch={() => {}} initialCollapsed={true} />

      {/* 결과 헤더 + 정렬 탭 */}
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 className="text-xl font-bold text-gray-900">검색 결과</h2>
            {totalElements !== null && (
              <p className="text-sm text-gray-500 mt-0.5">
                총 <strong className="text-gray-900">{totalElements.toLocaleString()}</strong>개 매물
              </p>
            )}
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {loading && <div className="spinner" />}
            {/* 모바일 전용 뷰 토글 */}
            <div className="sm:hidden flex gap-0.5 bg-gray-100 rounded-lg p-0.5">
              <button
                onClick={() => { setViewMode('card'); localStorage.setItem('search_view_mode', 'card') }}
                className={`p-1.5 rounded-md transition-all ${viewMode === 'card' ? 'bg-white shadow-sm text-gray-700' : 'text-gray-400'}`}
                aria-label="카드 뷰"
              >
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                  <rect x="3" y="3" width="18" height="11" rx="1.5"/>
                  <rect x="3" y="16.5" width="13" height="2" rx="1"/>
                  <rect x="3" y="20" width="9" height="2" rx="1"/>
                </svg>
              </button>
              <button
                onClick={() => { setViewMode('list'); localStorage.setItem('search_view_mode', 'list') }}
                className={`p-1.5 rounded-md transition-all ${viewMode === 'list' ? 'bg-white shadow-sm text-gray-700' : 'text-gray-400'}`}
                aria-label="리스트 뷰"
              >
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                  <rect x="3" y="3" width="6" height="6" rx="1"/>
                  <rect x="11" y="4.5" width="10" height="2" rx="1"/>
                  <rect x="11" y="7" width="7" height="1.5" rx="0.75"/>
                  <rect x="3" y="12" width="6" height="6" rx="1"/>
                  <rect x="11" y="13.5" width="10" height="2" rx="1"/>
                  <rect x="11" y="16" width="7" height="1.5" rx="0.75"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        {/* 정렬 탭 */}
        <div className="flex gap-1.5 overflow-x-auto pb-0.5" style={{ scrollbarWidth: 'none' }}>
          {SIMPLE_SORTS.map(o => (
            <button
              key={o.value}
              onClick={() => setSort(o.value)}
              className={`whitespace-nowrap px-3 py-1.5 rounded-full text-xs font-semibold transition-all shrink-0 ${
                currentSort === o.value
                  ? 'bg-brand-600 text-white shadow-sm'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {o.label}
            </button>
          ))}
          {TOGGLE_SORTS.map(o => {
            const isA = currentSort === o.a
            const isB = currentSort === o.b
            const isActive = isA || isB
            const label = isB ? o.labelB : o.labelA
            const nextSort = isA ? o.b : isB ? o.a : o.a
            return (
              <button
                key={o.a}
                onClick={() => setSort(nextSort)}
                className={`whitespace-nowrap inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-semibold transition-all shrink-0 ${
                  isActive
                    ? 'bg-brand-600 text-white shadow-sm'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {label}
                {isActive && (
                  <svg className="w-3 h-3 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5}
                      d={isA ? 'M5 15l7-7 7 7' : 'M19 9l-7 7-7-7'} />
                  </svg>
                )}
              </button>
            )
          })}
        </div>

        {/* 활성 필터 칩 (fixedParams 제외) */}
        {removableChips.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {removableChips.map(chip => (
              <button
                key={chip.key}
                onClick={() => removeChip(chip.removeKeys)}
                className="inline-flex items-center gap-1 bg-brand-50 text-brand-700 border border-brand-200 rounded-full px-3 py-1 text-xs font-medium hover:bg-red-50 hover:text-red-600 hover:border-red-200 transition-colors"
              >
                {chip.label}
                <svg className="w-3 h-3 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            ))}
            {removableChips.length > 1 && (
              <button
                onClick={clearAllFilters}
                className="inline-flex items-center gap-1 bg-gray-100 text-gray-500 rounded-full px-3 py-1 text-xs font-medium hover:bg-gray-200 transition-colors"
              >
                추가필터 초기화
              </button>
            )}
          </div>
        )}
      </div>

      {/* 광고 */}
      <AdSlot id="seo-page-banner" variant="banner" />

      {/* 카드 그리드 */}
      <div className={compact ? 'flex flex-col gap-2 sm:grid sm:grid-cols-2 lg:grid-cols-3 sm:gap-4' : 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4'}>
        {loading && filteredItems.length === 0
          ? Array.from({ length: 6 }).map((_, i) => <CarCardSkeleton key={i} compact={compact} />)
          : filteredItems.map((it, idx) => (
              <React.Fragment key={it.carId}>
                <div className="animate-fade-in" style={{ animationDelay: `${Math.min(idx, 5) * 0.03}s` }}>
                  <CarCard
                    item={it}
                    showLikeCount={false}
                    loadLikeOnMount={false}
                    initialLiked={likedCarIds.has(it.carId)}
                    savedItemCount={filteredItems.length}
                    compact={compact}
                    onLikeChanged={(carId, liked) => {
                      setLikedCarIds(prev => {
                        const next = new Set(prev)
                        if (liked) next.add(carId)
                        else next.delete(carId)
                        return next
                      })
                    }}
                  />
                </div>
              </React.Fragment>
            ))}
      </div>

      {!loading && filteredItems.length === 0 && (
        <div className="card p-12 text-center text-gray-400">
          <p className="text-lg mb-2">현재 등록된 매물이 없습니다</p>
          <p className="text-sm">잠시 후 다시 확인해 주세요</p>
        </div>
      )}

      <div ref={sentinelRef} className="h-4" />
    </div>
  )
}
