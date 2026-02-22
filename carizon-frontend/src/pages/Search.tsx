import React, { useEffect, useState, useMemo, useRef } from 'react'
import { useSearchParams, useLocation, Link, type Location } from 'react-router-dom'
import FiltersPanel from '@/components/FiltersPanel'
import CarCard from '@/components/CarCard'
import AdSlot from '@/components/AdSlot'
import { searchCars, type CarListItem } from '@/api/cars'
import { batchLikes } from '@/api/likes'
import { getRecommendations, type RecommendedCar } from '@/api/recommendations'

const AI_QUICK_PROMPTS = [
  '예산 2000만원 이하 SUV',
  '연비 좋은 출퇴근용 소형차',
  '첫차로 무사고 국산차',
  '3000만원 이하 수입차',
]

function AiChatPanel({ fromLocation }: { fromLocation: Location }) {
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [cars, setCars] = useState<RecommendedCar[]>([])
  const [asked, setAsked] = useState(false)

  const handleSend = async (q?: string) => {
    const query = (q ?? input).trim()
    if (!query || loading) return
    setInput('')
    setAsked(true)
    setLoading(true)
    setMessage('')
    setCars([])
    try {
      const res = await getRecommendations({ query, maxResults: 6 })
      setMessage(res.recommendation ?? '')
      setCars(res.cars ?? [])
    } catch {
      setMessage('AI 추천 서비스에 일시적인 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="card p-4 space-y-3">
      <div className="flex items-center gap-2">
        <div className="w-7 h-7 bg-brand-600 rounded-lg flex items-center justify-center text-sm">🤖</div>
        <div>
          <p className="text-sm font-bold text-gray-900">AI 매물 추천</p>
          <p className="text-[11px] text-gray-400">자연어로 원하는 차를 설명해보세요</p>
        </div>
        <Link to="/recommendation" className="ml-auto text-xs text-brand-600 font-medium hover:underline">전체 채팅 →</Link>
      </div>

      {!asked && (
        <div className="flex flex-wrap gap-1.5">
          {AI_QUICK_PROMPTS.map(p => (
            <button key={p} onClick={() => handleSend(p)}
              className="px-3 py-1.5 rounded-full bg-gray-100 hover:bg-brand-50 hover:text-brand-700 text-xs font-medium text-gray-600 transition-colors">
              {p}
            </button>
          ))}
        </div>
      )}

      <form onSubmit={e => { e.preventDefault(); handleSend() }} className="flex gap-2">
        <input
          className="input text-sm flex-1"
          placeholder="예) 가족용 SUV, 예산 3000만원 이하..."
          value={input}
          onChange={e => setInput(e.target.value)}
        />
        <button type="submit" disabled={loading || !input.trim()}
          className="btn-primary px-4 py-2 text-sm disabled:opacity-50">
          {loading ? <span className="spinner w-4 h-4" /> : '추천'}
        </button>
      </form>

      {asked && (
        <div className="space-y-2">
          {loading && (
            <div className="flex items-center gap-2 text-sm text-brand-700 py-2">
              <div className="spinner" />
              <span>AI가 매물을 분석하고 있어요...</span>
            </div>
          )}
          {!loading && message && (
            <p className="text-sm text-gray-700 bg-brand-50 rounded-xl px-4 py-3 whitespace-pre-wrap">{message}</p>
          )}
          {!loading && cars.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 pt-1">
              {cars.map(car => (
                <Link key={car.carId} to={`/cars/${car.carId}`}
                  state={{
                    from: `${fromLocation.pathname}${fromLocation.search}${fromLocation.hash}`,
                    source: 'search',
                    backgroundLocation: fromLocation,
                  }}
                  className="card-hover p-3 flex items-center gap-3 cursor-pointer">
                  {car.imageUrl ? (
                    <img src={car.imageUrl} alt={car.model}
                      className="w-16 h-12 object-cover rounded-lg shrink-0 bg-gray-100" />
                  ) : (
                    <div className="w-16 h-12 bg-gray-100 rounded-lg shrink-0 flex items-center justify-center text-xl">🚗</div>
                  )}
                  <div className="min-w-0">
                    <p className="text-sm font-bold text-gray-900 truncate">{car.maker} {car.model}</p>
                    <p className="text-xs text-gray-500 truncate">{car.trim}</p>
                    {car.price != null && (
                      <p className="text-sm font-bold text-brand-600">{(car.price / 10000).toFixed(0)}만원</p>
                    )}
                  </div>
                </Link>
              ))}
            </div>
          )}
          {!loading && cars.length === 0 && message && (
            <p className="text-sm text-gray-500 text-center py-2">조건에 맞는 매물을 찾지 못했습니다.</p>
          )}
        </div>
      )}
    </div>
  )
}

const SORT_OPTIONS = [
  { value: '', label: '무작위' },
  { value: 'RECENT', label: '최신순' },
  { value: 'LOW_PRICE', label: '가격 낮은순' },
  { value: 'LOW_KM', label: '주행 적은순' },
  { value: 'NEW_YEAR', label: '신형순' },
]

export default function Search() {
  const [sp, setSp] = useSearchParams()
  const fromLocation = useLocation()
  const [list, setList]               = useState<CarListItem[]>([])
  const [page, setPage]               = useState(0)
  const [totalPages, setTotalPages]   = useState(0)
  const [totalElements, setTotal]     = useState(0)
  const [loading, setLoading]         = useState(false)
  const [likesByCarId, setLikesByCarId] = useState<Record<number, number>>({})
  const [aiFallbackList, setAiFallbackList] = useState<CarListItem[]>([])
  const [aiFallbackMessage, setAiFallbackMessage] = useState('')
  const [aiFallbackLoading, setAiFallbackLoading] = useState(false)
  const aiRequestedQueryRef = useRef('')

  const params = useMemo(() => Object.fromEntries(sp.entries()), [sp])
  const textQuery = useMemo(() => String(sp.get('q') ?? '').trim(), [sp])
  const currentPage = useMemo(() => {
    const raw = Number(sp.get('page') ?? 0)
    return Number.isFinite(raw) && raw >= 0 ? Math.floor(raw) : 0
  }, [sp])
  const visibleList = useMemo(
    () => (list.length > 0 ? list : aiFallbackList),
    [list, aiFallbackList]
  )

  const fetchPage = async (p = 0) => {
    setLoading(true)
    try {
      const res = await searchCars({ ...params, page: p, size: 20 })
      setList(res?.content ?? [])
      setTotalPages(res?.totalPages ?? 0)
      setTotal(res?.totalElements ?? 0)
      setPage(p)
    } catch {
      setList([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchPage(currentPage) }, [sp.toString()])

  useEffect(() => {
    setAiFallbackList([])
    setAiFallbackMessage('')
    setAiFallbackLoading(false)
    aiRequestedQueryRef.current = ''
  }, [textQuery])

  useEffect(() => {
    if (loading) return
    if (!textQuery) return
    if (list.length > 0) return
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
  }, [loading, textQuery, list.length])

  useEffect(() => {
    let cancelled = false
    const carIds = visibleList.map(it => Number(it.carId)).filter(Number.isFinite)
    if (carIds.length === 0) {
      setLikesByCarId({})
      return
    }
    batchLikes(carIds)
      .then(data => {
        if (cancelled) return
        setLikesByCarId(data ?? {})
      })
      .catch(() => {
        if (cancelled) return
        setLikesByCarId({})
      })
    return () => {
      cancelled = true
    }
  }, [visibleList])

  const setFilters = (v: Record<string, any>) => {
    const usp = new URLSearchParams()
    Object.entries(v).forEach(([k, val]) => {
      if (val !== undefined && val !== null && val !== '') usp.set(k, String(val))
    })
    usp.set('page', '0')
    setSp(usp)
  }

  // 페이지 번호 배열
  const pageNums = (() => {
    const half = 2
    let start = Math.max(0, page - half)
    let end   = Math.min(totalPages - 1, start + 4)
    if (end - start < 4) start = Math.max(0, end - 4)
    return Array.from({ length: end - start + 1 }, (_, i) => start + i)
  })()

  const goPage = (p: number) => {
    const usp = new URLSearchParams(sp)
    usp.set('page', String(p))
    setSp(usp)
  }

  const setSort = (sort: string) => {
    const usp = new URLSearchParams(sp)
    if (sort) usp.set('sort', sort)
    else usp.delete('sort')
    usp.set('page', '0')
    setSp(usp)
  }

  const isTextFallbackMode = !loading && list.length === 0 && textQuery.length > 0

  return (
    <div className="space-y-5 animate-fade-in">
      {/* 상단 필터 바 */}
      <FiltersPanel value={params} onChange={setFilters} onSearch={() => goPage(0)} />

      {/* 결과 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">검색 결과</h2>
          {!loading && (
            <p className="text-sm text-gray-500 mt-0.5">
              총 <strong className="text-gray-900">{totalElements.toLocaleString()}</strong>개 매물
            </p>
          )}
          {!loading && list.length === 0 && aiFallbackList.length > 0 && (
            <p className="text-xs text-brand-600 mt-1 font-medium">
              검색결과가 없어 텍스트 기반 조건 추천 매물을 표시합니다.
            </p>
          )}
        </div>
        <div className="flex items-center gap-2">
          <select
            className="select text-sm min-w-[140px]"
            value={String(sp.get('sort') ?? '')}
            onChange={e => setSort(e.target.value)}
          >
            {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          {loading && <div className="spinner" />}
        </div>
      </div>

      {/* 광고 */}
      <AdSlot id="search-banner" variant="banner" />

      {/* AI 추천 채팅 */}
      <AiChatPanel fromLocation={fromLocation} />

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
        {visibleList
          .filter(it => it.priceMin != null || it.priceMax != null)
          .map((it, idx) => (
            <React.Fragment key={it.carId}>
              <div className="animate-fade-in" style={{ animationDelay: `${idx * 0.03}s` }}>
                <CarCard item={it} likesCount={likesByCarId[it.carId] ?? 0} />
              </div>
              {/* 9번째 카드 뒤에 그리드 내 광고 삽입 */}
              {idx === 8 && visibleList.length > 9 && (
                <div className="card flex items-center justify-center bg-gray-50 border-dashed border-gray-200 min-h-[200px]">
                  <AdSlot id="search-grid-ad" variant="rectangle" />
                </div>
              )}
            </React.Fragment>
          ))}
      </div>

      {/* 결과 없음 */}
      {!loading && !aiFallbackLoading && list.length === 0 && aiFallbackList.length === 0 && (
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

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-1 pt-4 flex-wrap">
          <button
            onClick={() => goPage(0)}
            disabled={page === 0}
            className="p-2 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed transition hidden sm:block"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
            </svg>
          </button>
          <button
            onClick={() => goPage(Math.max(0, page - 1))}
            disabled={page === 0}
            className="p-2 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed transition"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          {pageNums.map(p => (
            <button
              key={p}
              onClick={() => goPage(p)}
              className={`min-w-[2.25rem] h-9 rounded-lg text-sm font-semibold transition-all ${
                p === page
                  ? 'bg-brand-600 text-white shadow-sm'
                  : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              {p + 1}
            </button>
          ))}
          <button
            onClick={() => goPage(Math.min(totalPages - 1, page + 1))}
            disabled={page >= totalPages - 1}
            className="p-2 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed transition"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
          <button
            onClick={() => goPage(totalPages - 1)}
            disabled={page >= totalPages - 1}
            className="p-2 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed transition hidden sm:block"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 5l7 7-7 7M5 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      )}
    </div>
  )
}
