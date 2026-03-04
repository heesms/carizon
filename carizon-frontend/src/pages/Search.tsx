import React, { useCallback, useEffect, useState, useMemo, useRef } from 'react'
import { useSearchParams, useLocation, useNavigationType } from 'react-router-dom'
import FiltersPanel from '@/components/FiltersPanel'
import CarCard from '@/components/CarCard'
import AdSlot from '@/components/AdSlot'
import { searchCars, type CarListItem } from '@/api/cars'
import { getMyLikes } from '@/api/likes'
import { getRecommendations } from '@/api/recommendations'
import { trackFilterApply, trackSearch } from '@/lib/analytics'

const SIMPLE_SORTS = [
  { value: '', label: '추천순' },
  { value: 'RECENT', label: '최신순' },
] as const

// 두 번 클릭 시 반전되는 정렬: [기본값, 반전값]
const TOGGLE_SORTS = [
  { a: 'LOW_PRICE',  b: 'HIGH_PRICE', labelA: '가격 낮은순', labelB: '가격 높은순' },
  { a: 'LOW_KM',    b: 'HIGH_KM',    labelA: '주행 적은순', labelB: '주행 많은순' },
  { a: 'NEW_YEAR',  b: 'OLD_YEAR',   labelA: '신형순',       labelB: '구형순' },
] as const

/** 로딩 중 표시할 스켈레톤 카드 */
function CarCardSkeleton() {
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

/** URL 파라미터 → 활성 필터 칩 목록 */
function buildActiveChips(params: Record<string, string>) {
  const chips: Array<{ key: string; label: string; removeKeys: string[] }> = []

  if (params.q)
    chips.push({ key: 'q', label: `🔍 "${params.q}"`, removeKeys: ['q'] })

  if (params.makerCode)
    chips.push({ key: 'maker', label: '🚗 제조사', removeKeys: ['makerCode', 'modelGroupCode', 'modelCode', 'trimCode'] })
  else if (params.modelCode)
    chips.push({ key: 'model', label: '🚗 모델', removeKeys: ['modelCode', 'trimCode'] })

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

  if (params.bodyType)
    chips.push({ key: 'bodyType', label: '🚙 차체', removeKeys: ['bodyType'] })

  if (params.color)
    chips.push({ key: 'color', label: '🎨 색상', removeKeys: ['color'] })

  return chips
}

export default function Search() {
  const [sp, setSp] = useSearchParams()
  const location = useLocation()
  const navigationType = useNavigationType()
  const [items, setItems]             = useState<CarListItem[]>([])
  const [likedCarIds, setLikedCarIds] = useState<Set<number>>(() => new Set())
  const [totalElements, setTotal]     = useState<number | null>(null)
  const [loading, setLoading]         = useState(false)
  const [hasMore, setHasMore]         = useState(false)
  const [aiFallbackList, setAiFallbackList] = useState<CarListItem[]>([])
  const [aiFallbackMessage, setAiFallbackMessage] = useState('')
  const [aiFallbackLoading, setAiFallbackLoading] = useState(false)
  const aiRequestedQueryRef = useRef('')
  const savedScrollRef      = useRef<number | null>(null)
  const busyRef             = useRef(false)
  const nextPageRef         = useRef(0)
  const sentinelRef         = useRef<HTMLDivElement>(null)
  const paramsRef           = useRef<Record<string, string>>({})
  const savedItemCountRef   = useRef(0)
  const loadPageRef         = useRef<(pageNum: number, reset: boolean) => Promise<void>>(async () => {})
  paramsRef.current = useMemo(() => Object.fromEntries(sp.entries()), [sp])

  // 뒤로가기 시 스크롤 위치 복원
  useEffect(() => {
    const routeState = (location.state ?? {}) as { restoreSearchScrollFromDetail?: boolean }
    const shouldRestore = routeState.restoreSearchScrollFromDetail === true
      || navigationType === 'POP'

    const raw = sessionStorage.getItem('search_scroll_y')
    if (!raw) return
    if (!shouldRestore) {
      sessionStorage.removeItem('search_scroll_y')
      return
    }
    try {
      const { url, y, itemCount } = JSON.parse(raw)
      if (url === `${location.pathname}${location.search}`) {
        sessionStorage.removeItem('search_scroll_y')
        savedScrollRef.current = y
        savedItemCountRef.current = Number(itemCount) || 0
      } else {
        sessionStorage.removeItem('search_scroll_y')
      }
    } catch {
      sessionStorage.removeItem('search_scroll_y')
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const params = paramsRef.current
  const textQuery = useMemo(() => String(sp.get('q') ?? '').trim(), [sp])
  const visibleList = useMemo(
    () => (items.length > 0 ? items : aiFallbackList),
    [items, aiFallbackList]
  )
  const filteredVisibleList = useMemo(
    () => visibleList.filter((item) => {
      const min = item.priceMin
      const max = item.priceMax
      return (typeof min === 'number' && min >= 1) || (typeof max === 'number' && max >= 1)
    }),
    [visibleList]
  )

  const activeChips = useMemo(() => buildActiveChips(params), [params])

  // 뒤로가기 시 스크롤 복원: 이전에 로드됐던 아이템 수만큼 채운 후 스크롤 복원
  useEffect(() => {
    if (savedScrollRef.current === null) return
    if (filteredVisibleList.length === 0) return
    const targetCount = savedItemCountRef.current
    // 아직 부족하고 더 로드할 수 있으면 다음 페이지 먼저 로드
    if (targetCount > 0 && filteredVisibleList.length < targetCount && hasMore && !loading) {
      loadPageRef.current(nextPageRef.current, false)
      return
    }
    // 충분히 로드됐거나 더 이상 없으면 로딩 끝날 때 스크롤 복원
    if (loading) return
    const y = savedScrollRef.current
    savedScrollRef.current = null
    savedItemCountRef.current = 0
    requestAnimationFrame(() => window.scrollTo({ top: y, behavior: 'instant' as ScrollBehavior }))
  }, [loading, filteredVisibleList.length, hasMore])

  useEffect(() => {
    let cancelled = false
    getMyLikes(500)
      .then((data) => {
        if (cancelled) return
        const ids = Array.isArray(data?.carIds) ? data.carIds : []
        const next = new Set(
          ids
            .map((id) => Number(id))
            .filter((id) => Number.isFinite(id))
        )
        setLikedCarIds(next)
      })
      .catch(() => {
        // no-op
      })
    return () => { cancelled = true }
  }, [])

  // eslint-disable-next-line prefer-const
  const loadPage = useCallback(async (pageNum: number, reset: boolean) => {
    if (busyRef.current) return
    busyRef.current = true
    setLoading(true)
    try {
      const res = await searchCars({ ...paramsRef.current, page: pageNum, size: 30 })
      const content = res?.content ?? []
      if (reset) setItems(content)
      else setItems(prev => [...prev, ...content])
      const tp = res?.totalPages ?? 0
      setHasMore(pageNum + 1 < tp)
      nextPageRef.current = pageNum + 1
      setTotal(res?.totalElements ?? 0)
    } catch {
      if (reset) setItems([])
    } finally {
      setLoading(false)
      busyRef.current = false
    }
  }, [])
  loadPageRef.current = loadPage

  // 필터/정렬 변경 시 초기화 후 첫 페이지 로드
  useEffect(() => {
    setItems([])
    setHasMore(false)
    nextPageRef.current = 0
    loadPage(0, true)
  }, [sp.toString()]) // eslint-disable-line react-hooks/exhaustive-deps

  // 무한 스크롤: sentinel이 뷰포트에 들어오면 다음 페이지 로드
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

  useEffect(() => {
    setAiFallbackList([])
    setAiFallbackMessage('')
    setAiFallbackLoading(false)
    aiRequestedQueryRef.current = ''
  }, [textQuery])

  useEffect(() => {
    if (loading) return
    if (!textQuery) return
    if (items.length > 0) return
    if (aiRequestedQueryRef.current === textQuery) return

    let cancelled = false
    aiRequestedQueryRef.current = textQuery
    setAiFallbackLoading(true)

    getRecommendations({ query: textQuery, maxResults: 12, useLlm: false })
      .then(res => {
        if (cancelled) return
        const mapped = (res?.cars ?? []).reduce<CarListItem[]>((acc, car) => {
            const carId = Number(car.carId)
            if (!Number.isFinite(carId)) return acc
            acc.push({
              carId,
              maker: car.maker ?? '',
              model: car.model ?? '',
              trim: car.trim,
              year: car.year,
              km: car.mileage,
              priceMin: car.price,
              priceMax: car.price,
              representativeImageUrl: car.imageUrl,
              modelCode: undefined,
              fuel: car.fuel,
              region: car.region,
            })
            return acc
          }, [])
        setAiFallbackList(mapped)
        setAiFallbackMessage(res?.recommendation ?? '')
      })
      .catch(() => {
        if (cancelled) return
        setAiFallbackList([])
        setAiFallbackMessage('')
      })
      .finally(() => {
        if (cancelled) return
        setAiFallbackLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [loading, textQuery, items.length])

  const setFilters = (v: Record<string, any>) => {
    if (v.q) trackSearch(v.q)
    else trackFilterApply(v)
    const usp = new URLSearchParams()
    Object.entries(v).forEach(([k, val]) => {
      if (val !== undefined && val !== null && val !== '') usp.set(k, String(val))
    })
    setSp(usp)
  }

  const removeChip = (removeKeys: string[]) => {
    const usp = new URLSearchParams(sp)
    removeKeys.forEach(k => usp.delete(k))
    setSp(usp)
  }

  const clearAllFilters = () => setSp(new URLSearchParams())

  const setSort = (sort: string) => {
    const usp = new URLSearchParams(sp)
    if (sort) usp.set('sort', sort)
    else usp.delete('sort')
    setSp(usp)
  }

  const currentSort = sp.get('sort') ?? ''
  const isTextFallbackMode = !loading && items.length === 0 && textQuery.length > 0

  return (
    <div className="space-y-5 animate-fade-in">
      {/* 상단 필터 바 */}
      <FiltersPanel value={params} onChange={setFilters} onSearch={() => {}} />

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
            {!loading && items.length === 0 && filteredVisibleList.length > 0 && (
              <p className="text-xs text-brand-600 mt-1 font-medium">
                검색결과가 없어 텍스트 기반 조건 추천 매물을 표시합니다.
              </p>
            )}
          </div>
          {loading && <div className="spinner shrink-0" />}
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

        {/* 활성 필터 칩 */}
        {activeChips.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {activeChips.map(chip => (
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
            {activeChips.length > 1 && (
              <button
                onClick={clearAllFilters}
                className="inline-flex items-center gap-1 bg-gray-100 text-gray-500 rounded-full px-3 py-1 text-xs font-medium hover:bg-gray-200 transition-colors"
              >
                전체 초기화
              </button>
            )}
          </div>
        )}
      </div>

      {/* 광고 */}
      <AdSlot id="search-banner" variant="banner" />

      {isTextFallbackMode && (
        <div className="card p-4 border border-brand-100 bg-brand-50/50">
          {aiFallbackLoading ? (
            <div className="flex items-center gap-2 text-sm text-brand-700">
              <div className="spinner" />
              <p>검색결과가 없어서 텍스트 기반 조건 추천 매물을 검색 중입니다.</p>
            </div>
          ) : aiFallbackMessage ? (
            <p className="text-sm text-gray-700 whitespace-pre-wrap">{aiFallbackMessage}</p>
          ) : (
            <p className="text-sm text-gray-600">텍스트 기반 조건 추천 결과를 준비했습니다.</p>
          )}
        </div>
      )}

      {/* 카드 그리드 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {loading && filteredVisibleList.length === 0
          ? Array.from({ length: 6 }).map((_, i) => <CarCardSkeleton key={i} />)
          : filteredVisibleList.map((it, idx) => (
              <React.Fragment key={it.carId}>
                <div className="animate-fade-in" style={{ animationDelay: `${Math.min(idx, 5) * 0.03}s` }}>
                  <CarCard
                    item={it}
                    showLikeCount={false}
                    loadLikeOnMount={false}
                    initialLiked={likedCarIds.has(it.carId)}
                    savedItemCount={filteredVisibleList.length}
                    onLikeChanged={(carId, liked) => {
                      setLikedCarIds((prev) => {
                        const next = new Set(prev)
                        if (liked) next.add(carId)
                        else next.delete(carId)
                        return next
                      })
                    }}
                  />
                </div>
                {/* 9번째 카드 뒤에 그리드 내 광고 삽입 */}
                {idx === 8 && filteredVisibleList.length > 9 && (
                  <div className="card flex items-center justify-center bg-gray-50 border-dashed border-gray-200 min-h-[200px]">
                    <AdSlot id="search-grid-ad" variant="rectangle" />
                  </div>
                )}
              </React.Fragment>
            ))
        }
      </div>

      {/* 결과 없음 */}
      {!loading && !aiFallbackLoading && filteredVisibleList.length === 0 && (
        <div className="card p-12 text-center">
          <div className="text-5xl mb-4">🔍</div>
          <p className="text-gray-600 font-medium mb-1">
            {textQuery ? '조건에 맞는 결과와 조건 추천 매물이 없습니다' : '조건에 맞는 결과가 없습니다'}
          </p>
          <p className="text-sm text-gray-400">
            {textQuery ? '검색어를 조금 더 구체적으로 바꿔보세요' : '필터를 조정하거나 다른 키워드로 검색해보세요'}
          </p>
        </div>
      )}

      {/* 무한 스크롤 sentinel / 로딩 인디케이터 */}
      <div ref={sentinelRef} className="flex justify-center py-6">
        {loading && filteredVisibleList.length > 0 && (
          <div className="flex items-center gap-2 text-sm text-gray-400">
            <div className="spinner" />
            <span>불러오는 중...</span>
          </div>
        )}
      </div>

    </div>
  )
}
