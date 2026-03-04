import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams, useLocation } from 'react-router-dom'
import CarCard from '@/components/CarCard'
import { searchCars, type CarListItem } from '@/api/cars'
import { getMyLikes } from '@/api/likes'

const SORT_OPTIONS = [
  { value: '', label: '추천순' },
  { value: 'RECENT', label: '최신순' },
  { value: 'LOW_PRICE', label: '가격 낮은순' },
  { value: 'HIGH_PRICE', label: '가격 높은순' },
  { value: 'LOW_KM', label: '주행 적은순' },
  { value: 'NEW_YEAR', label: '신형순' },
]

function Skeleton({ compact }: { compact: boolean }) {
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
        </div>
        <div className="mt-auto h-6 bg-gray-200 rounded w-2/5" />
      </div>
    </div>
  )
}

type Props = {
  /** 고정 필터 (브랜드/모델/차종 코드 등) - URL params와 병합됨 */
  fixedParams: Record<string, string>
}

/**
 * SEO 전용관 페이지에서 사용하는 차량 목록 섹션.
 * 기존 /search 페이지와 독립적으로 동작하며, fixedParams로 고정 필터를 지정한다.
 */
export default function SeoCarsSection({ fixedParams }: Props) {
  const location = useLocation()
  const [sp, setSp] = useSearchParams()

  const sort = sp.get('sort') ?? ''
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
      const res = await searchCars({ ...fixedParams, sort, page: pageNum, size: 30 })
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
  }, [sort, JSON.stringify(fixedParams)]) // eslint-disable-line react-hooks/exhaustive-deps

  // sort 또는 fixedParams 변경 시 초기화
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

  const setSort = (value: string) => {
    setSp(prev => {
      const next = new URLSearchParams(prev)
      if (value) next.set('sort', value)
      else next.delete('sort')
      return next
    }, { replace: true })
  }

  const compact = viewMode === 'list'

  return (
    <div>
      {/* 정렬 + 뷰 토글 툴바 */}
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-1.5 flex-wrap">
          {totalElements != null && (
            <span className="text-sm text-gray-500 mr-1">
              {totalElements.toLocaleString()}대
            </span>
          )}
          {SORT_OPTIONS.map(opt => (
            <button
              key={opt.value}
              onClick={() => setSort(opt.value)}
              className={`text-xs px-2.5 py-1 rounded-full border transition-colors ${
                sort === opt.value
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-blue-400'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
        {/* 모바일 뷰 토글 */}
        <button
          className="sm:hidden text-gray-400 hover:text-gray-700 p-1"
          onClick={() => {
            const next = viewMode === 'card' ? 'list' : 'card'
            setViewMode(next)
            localStorage.setItem('search_view_mode', next)
          }}
          aria-label={viewMode === 'card' ? '리스트뷰' : '카드뷰'}
        >
          {viewMode === 'card' ? (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
            </svg>
          ) : (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
            </svg>
          )}
        </button>
      </div>

      {/* 차량 그리드 */}
      <div className={compact
        ? 'flex flex-col gap-2'
        : 'grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3'
      }>
        {filteredItems.map(item => (
          <CarCard
            key={item.carId}
            item={item}
            compact={compact}
            initialLiked={likedCarIds.has(item.carId)}
            loadLikeOnMount={false}
            onLikeChanged={(carId, liked) => {
              setLikedCarIds(prev => {
                const next = new Set(prev)
                if (liked) next.add(carId)
                else next.delete(carId)
                return next
              })
            }}
          />
        ))}
        {loading && Array.from({ length: 10 }).map((_, i) => (
          <Skeleton key={i} compact={compact} />
        ))}
      </div>

      {/* 결과 없음 */}
      {!loading && filteredItems.length === 0 && (
        <div className="text-center py-20 text-gray-400">
          <p className="text-lg mb-2">현재 등록된 매물이 없습니다</p>
          <p className="text-sm">잠시 후 다시 확인해 주세요</p>
        </div>
      )}

      <div ref={sentinelRef} className="h-4" />
    </div>
  )
}
