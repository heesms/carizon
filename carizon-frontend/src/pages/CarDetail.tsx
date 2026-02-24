import React, { useEffect, useState } from 'react'
import { useParams, Link, useLocation, useNavigate } from 'react-router-dom'
import { getCarDetail, getPriceHistory, type CarDetailData, type PricePoint, type PlatformRow } from '@/api/cars'
import PriceChart from '@/components/PriceChart'
import LikeButton from '@/components/LikeButton'
import AdSlot from '@/components/AdSlot'
import { trackViewItem, trackPlatformLinkClick } from '@/lib/analytics'
import { applyCarDetailSeo, clearCarDetailSeo } from '@/utils/seo'

const NO_IMAGE = `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300"><rect fill="#f3f4f6" width="400" height="300"/><text fill="#9ca3af" font-family="sans-serif" font-size="13" x="200" y="158" text-anchor="middle">이미지 없음</text><rect fill="#e5e7eb" x="170" y="110" width="60" height="38" rx="4"/></svg>')}`

/** null / "null" / 빈문자열 → undefined 로 정리 */
const clean = (v: any): string | undefined => {
  if (v == null) return undefined
  const s = String(v).trim()
  return (s && s !== 'null') ? s : undefined
}

// 플랫폼 한글명
const PLATFORM_NAMES: Record<string, string> = {
  encar: '엔카', ENCAR: '엔카',
  kcar: 'K캐어', KCAR: 'K캐어',
  chachacha: '차차차', CHACHACHA: '차차차',
  chutcha: '첫차', CHUTCHA: '첫차',
  charancha: '차란차', CHARANCHA: '차란차',
  tcar: 'TCAR', TCAR: 'TCAR',
}
const platformLabel = (p: string) => PLATFORM_NAMES[p] ?? p

// 플랫폼 바 차트 색상 (bg / text)
const PLATFORM_BAR_COLORS: Record<string, { bg: string; text: string; light: string }> = {
  ENCAR:     { bg: 'bg-red-500',    text: 'text-red-700',    light: 'bg-red-50' },
  KCAR:      { bg: 'bg-blue-500',   text: 'text-blue-700',   light: 'bg-blue-50' },
  CHACHACHA: { bg: 'bg-yellow-500', text: 'text-yellow-700', light: 'bg-yellow-50' },
  CHUTCHA:   { bg: 'bg-green-500',  text: 'text-green-700',  light: 'bg-green-50' },
  CHARANCHA: { bg: 'bg-purple-500', text: 'text-purple-700', light: 'bg-purple-50' },
  TCAR:      { bg: 'bg-orange-500', text: 'text-orange-700', light: 'bg-orange-50' },
}
const barColor = (p: string) => PLATFORM_BAR_COLORS[p.toUpperCase()] ?? { bg: 'bg-gray-400', text: 'text-gray-700', light: 'bg-gray-50' }

function useIsMobile() {
  const [mobile, setMobile] = useState(false)
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 767px)')
    const update = () => setMobile(mq.matches)
    update(); mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [])
  return mobile
}

/** PC/모바일 자동 감지하여 적절한 URL 반환 */
function platformUrl(p: PlatformRow, isMobile: boolean) {
  if (isMobile) return p.mUrl || p.pcUrl
  return p.pcUrl || p.mUrl
}

export default function CarDetail({ carId: carIdProp, onClose }: { carId?: number; onClose?: () => void } = {}) {
  const { id: idParam } = useParams<{ id: string }>()
  const id = carIdProp != null ? String(carIdProp) : idParam
  const location = useLocation()
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const [detail, setDetail]   = useState<CarDetailData | null>(null)
  const [history, setHistory] = useState<PricePoint[]>([])
  const [imgSrc, setImgSrc]   = useState(NO_IMAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)
  const routeState = (location.state ?? {}) as { from?: string; source?: string }
  const source = routeState.source === 'ai'
    ? 'ai'
    : routeState.source === 'search'
      ? 'search'
      : 'other'
  const isModal = typeof onClose === 'function'

  const handleClose = () => {
    if (onClose) { onClose(); return }
    const from = typeof routeState.from === 'string' ? routeState.from : ''
    if (source === 'ai') {
      if (window.history.length > 1) { navigate(-1); return }
      navigate('/recommendation')
      return
    }
    if (from) { navigate(from); return }
    navigate('/search')
  }

  const backLabel = source === 'ai'
    ? 'AI 채팅으로'
    : source === 'search'
      ? '검색 결과로'
      : '이전 화면으로'
  const closeButton = (
    <button
      type="button"
      onClick={handleClose}
      aria-label="상세 닫기"
      className="h-9 w-9 inline-flex items-center justify-center rounded-full bg-gray-100 hover:bg-gray-200 transition-colors"
    >
      <span className="sr-only">닫기</span>
      <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
      </svg>
    </button>
  )
  const modalHeader = isModal ? (
    <div className="sticky top-0 z-20 bg-white/95 backdrop-blur-sm border-b border-gray-100 px-4 sm:px-6 py-2.5 flex items-center justify-between">
      <span className="text-sm font-semibold text-gray-700 truncate mr-3">차량 상세</span>
      {closeButton}
    </div>
  ) : null

  useEffect(() => {
    if (!id) return
    setDetail(null)
    setHistory([])
    setImgSrc(NO_IMAGE)
    setLoading(true)
    setError(null)
    getCarDetail(id)
      .then(data => {
        setDetail(data)
        const url = data.car.representativeImageUrl
        const modelImgUrl = data.car.modelCode ? `/image/car/model/${data.car.modelCode}.webp` : undefined
        if (url) setImgSrc(url)
        else if (modelImgUrl) setImgSrc(modelImgUrl)

        const activePrices = (data.platformRows ?? [])
          .filter(p => p.price != null && p.price > 0 && p.status !== 'SOLD')
          .map(p => p.price!)
        const minPriceForSeo = activePrices.length > 0 ? Math.min(...activePrices) : undefined

        trackViewItem(Number(id), data.car.maker ?? '', data.car.model ?? '', minPriceForSeo)

        if (!isModal) {
          applyCarDetailSeo({
            id,
            maker: data.car.maker,
            model: data.car.model,
            trim: data.car.trim,
            year: data.car.year,
            mileage: data.car.mileage,
            fuel: data.car.fuel,
            price: minPriceForSeo,
            imageUrl: url || modelImgUrl,
          })
        }
      })
      .catch(() => setError('차량 정보를 불러올 수 없습니다.'))
      .finally(() => setLoading(false))

    getPriceHistory(id)
      .then(res => setHistory(res.points ?? []))
      .catch(() => {})

    return () => {
      if (!isModal) clearCarDetailSeo()
    }
  }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return (
    <div className={`animate-fade-in ${isModal ? 'pb-4' : ''}`}>
      {modalHeader}
      <div className="flex items-center justify-center py-32">
        <div className="spinner w-8 h-8" />
      </div>
    </div>
  )
  if (error || !detail) return (
    <div className="card p-8 sm:p-12 text-center">
      {modalHeader}
      <div className="text-4xl mb-3">😢</div>
      <p className="text-gray-600">{error ?? '데이터가 없습니다.'}</p>
      {isModal ? (
        <button type="button" onClick={handleClose} className="btn-primary mt-4">
          닫기
        </button>
      ) : (
        <Link to="/search" className="btn-primary mt-4 inline-flex">← 검색으로 돌아가기</Link>
      )}
    </div>
  )

  const car = detail.car
  const platforms = detail.platformRows ?? []
  const activePlatforms = platforms.filter(p => p.price != null && p.price > 0 && p.status !== 'SOLD')
  const soldPlatforms = platforms.filter(p => p.status === 'SOLD')
  const minPrice = activePlatforms.length > 0 ? Math.min(...activePlatforms.map(p => p.price!)) : null
  const maxPrice = activePlatforms.length > 0 ? Math.max(...activePlatforms.map(p => p.price!)) : null
  const hasUniqueLowestPrice = minPrice != null && activePlatforms.filter(p => p.price === minPrice).length === 1

  const specs = [
    { label: '연식',     value: car.year ? `${car.year}년식` : undefined },
    { label: '주행거리', value: car.mileage ? `${car.mileage.toLocaleString()}km` : undefined },
    { label: '배기량',   value: car.displacement ? `${car.displacement.toLocaleString()}cc` : undefined },
    { label: '연료',     value: clean(car.fuel) },
    { label: '변속기',   value: clean(car.transmission) },
    { label: '차체',     value: clean(car.bodyType) },
    { label: '인승',     value: car.seatCount != null ? `${car.seatCount}인승` : undefined },
    { label: '색상',     value: clean(car.color) },
    { label: '지역',     value: clean(car.region) },
    { label: '차량번호', value: clean(car.carNo) },
    { label: '신차가',   value: car.priceNew ? `${car.priceNew.toLocaleString()}만원` : undefined },
  ].filter(s => s.value)

  return (
    <div className={`space-y-4 sm:space-y-6 animate-fade-in ${isModal ? 'px-3 sm:px-6 lg:px-7 pb-8' : ''}`}>
      {/* 뒤로 (페이지 모드에서만 표시) */}
      {modalHeader}
      {!onClose && (
        <button type="button" onClick={handleClose} className="btn-ghost text-gray-500 inline-flex">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          {backLabel}
        </button>
      )}

      <div className={`grid grid-cols-1 gap-4 sm:gap-6 ${isModal ? 'md:grid-cols-[360px_minmax(0,1fr)] xl:grid-cols-[400px_minmax(0,1fr)]' : 'lg:grid-cols-[380px_1fr]'}`}>
        {/* 좌측: 이미지 + 가격 */}
        <div className="space-y-4">
          <div className="card overflow-hidden">
            <div className="aspect-[4/3] bg-gray-50">
              <img
                src={imgSrc}
                alt={[clean(car.maker), clean(car.model)].filter(Boolean).join(' ') || '차량'}
                className="w-full h-full object-cover object-[center_65%]"
                onError={() => setImgSrc(NO_IMAGE)}
              />
            </div>
            <div className="p-4 sm:p-5">
              <h1 className="text-xl sm:text-2xl font-black text-gray-900 mb-0.5">
                {[clean(car.maker), clean(car.model)].filter(Boolean).join(' ') || '차량 정보'}
              </h1>
              {clean(car.trim) && <p className="text-gray-500 text-sm mb-1">{car.trim}</p>}

              {/* 핵심 스펙 태그 */}
              <div className="flex flex-wrap gap-1.5 mb-3">
                {!!car.year && <span className="badge badge-gray text-xs">{car.year}년</span>}
                {!!car.mileage && <span className="badge badge-gray text-xs">{car.mileage.toLocaleString()}km</span>}
                {!!clean(car.fuel) && <span className="badge badge-blue text-xs">{car.fuel}</span>}
                {!!car.displacement && <span className="badge badge-gray text-xs">{car.displacement.toLocaleString()}cc</span>}
              </div>

              {/* 가격 */}
              {minPrice && (
                <div className="mb-4">
                  <div className="text-xs text-gray-400 mb-0.5">
                    {hasUniqueLowestPrice ? '플랫폼 최저가' : '플랫폼 최저가격'}
                  </div>
                  <div className="text-2xl sm:text-3xl font-black text-brand-600">
                    {minPrice.toLocaleString()}만원
                    {maxPrice && maxPrice !== minPrice && (
                      <span className="text-base sm:text-lg text-gray-400 font-medium ml-1">~ {maxPrice.toLocaleString()}만원</span>
                    )}
                  </div>
                </div>
              )}

              {/* 좋아요 */}
              <div className="flex items-center gap-2">
                <LikeButton carId={Number(id)} />
              </div>
            </div>
          </div>

          {/* 상단 사이드 광고: 낮은 높이 */}
          <AdSlot id="detail-side" variant="leaderboard" />
        </div>

        {/* 우측: 상세 정보 */}
        <div className="space-y-4">
          {/* 기본 스펙 */}
          <div className="card p-4 sm:p-5">
            <h2 className="font-bold text-gray-900 mb-3 sm:mb-4 flex items-center gap-2">
              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              기본 정보
            </h2>
            {car.myAccidentCnt != null && car.myAccidentCnt > 0 && (
              <div className="flex items-center gap-2 mb-3 px-3 py-2 rounded-xl bg-red-50 border border-red-200">
                <svg className="w-5 h-5 text-red-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    d="M8 18v-3a4 4 0 018 0v3Z"
                    fill="currentColor"
                    fillOpacity="0.24"
                    stroke="none"
                  />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M8 18v-3a4 4 0 018 0v3M7 18h10M12 4v2M5 12h2M17 12h2M7 8.5l1.5 1.5M17 8.5l-1.5 1.5" />
                </svg>
                <p className="text-xs text-red-700 font-semibold">
                  내 차 사고이력 의심 - 구매 전 정비 이력을 꼭 확인하세요
                </p>
              </div>
            )}
            <dl className="grid grid-cols-2 sm:grid-cols-3 gap-2 sm:gap-3">
              {specs.map(s => (
                <div key={s.label} className="bg-gray-50 rounded-xl p-2.5 sm:p-3">
                  <dt className="text-[11px] sm:text-xs text-gray-400 mb-0.5">{s.label}</dt>
                  <dd className="font-semibold text-gray-900 text-xs sm:text-sm">{s.value}</dd>
                </div>
              ))}
            </dl>
          </div>

          {/* 플랫폼별 가격 비교 - 바 차트 */}
          <div className="card p-4 sm:p-5">
            <h2 className="font-bold text-gray-900 mb-3 sm:mb-4 flex items-center gap-2">
              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M3 6l3 1m0 0l-3 9a5.002 5.002 0 006.001 0M6 7l3 9M6 7l6-2m6 2l3-1m-3 1l-3 9a5.002 5.002 0 006.001 0M18 7l3 9m-3-9l-6-2m0-2v2m0 16V5m0 16H9m3 0h3" />
              </svg>
              플랫폼별 가격 비교
            </h2>

            {platforms.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-4">플랫폼 데이터가 없습니다</p>
            ) : (
              <div className="space-y-2.5">
                {/* 판매중 플랫폼 바 차트 */}
                {activePlatforms
                  .sort((a, b) => (a.price ?? 0) - (b.price ?? 0))
                  .map((p) => {
                    const url = platformUrl(p, isMobile)
                    const colors = barColor(p.platformName)
                    const maxBasePrice = maxPrice ?? 0
                    const barWidth = maxBasePrice > 0 && p.price != null
                      ? Math.max(8, Math.min(100, (p.price / maxBasePrice) * 100))
                      : 100
                    const isLowest = hasUniqueLowestPrice && minPrice != null && p.price === minPrice

                    return (
                      <a
                        key={p.platformCarId}
                        href={url ?? '#'}
                        target={url ? '_blank' : undefined}
                        rel="noopener noreferrer"
                        className={`block group rounded-xl p-3 transition-all hover:shadow-md ${colors.light} ${!url ? 'pointer-events-none' : ''}`}
                        onClick={() => url && trackPlatformLinkClick(p.platformName ?? '', Number(id))}
                      >
                        <div className="flex items-center justify-between mb-1.5">
                          <div className="flex items-center gap-2">
                            <span className={`text-sm font-bold ${colors.text} group-hover:underline`}>
                              {platformLabel(p.platformName)}
                            </span>
                            {isLowest && (
                              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-brand-600 text-white">최저가</span>
                            )}
                            <span className="badge badge-green text-[10px]">판매중</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm sm:text-base font-black text-gray-900">
                              {p.price?.toLocaleString()}만원
                            </span>
                            {url && (
                              <svg className="w-4 h-4 text-gray-400 group-hover:text-brand-600 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                              </svg>
                            )}
                          </div>
                        </div>
                        {/* 가격 바 */}
                        <div className="w-full bg-white/60 rounded-full h-2.5 overflow-hidden">
                          <div
                            className={`h-full rounded-full ${colors.bg} transition-all duration-500`}
                            style={{ width: `${barWidth}%`, opacity: isLowest ? 1 : 0.6 }}
                          />
                        </div>
                        {p.lastSeenDate && (
                          <div className="text-[10px] text-gray-400 mt-1">{p.lastSeenDate} 확인</div>
                        )}
                      </a>
                    )
                  })}

                {/* 판매완료 플랫폼 */}
                {soldPlatforms.length > 0 && (
                  <div className="pt-2 border-t border-gray-100">
                    <p className="text-[11px] text-gray-400 mb-1.5">판매 완료</p>
                    {soldPlatforms.map(p => (
                      <div key={p.platformCarId} className="flex items-center justify-between py-1.5 px-3 rounded-lg bg-gray-50 opacity-60 mb-1">
                        <span className="text-xs font-medium text-gray-500">{platformLabel(p.platformName)}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-gray-400 line-through">{p.price?.toLocaleString()}만원</span>
                          <span className="badge badge-red text-[10px]">판매완료</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* 가격 히스토리 차트 */}
          {history.length > 0 && <PriceChart points={history} />}

          {/* AI 추천 유도 */}
          <div className="card p-4 sm:p-5 bg-gradient-to-br from-brand-50 to-blue-50 border-brand-100">
            <div className="flex items-start gap-3">
              <span className="text-2xl">🤖</span>
              <div>
                <h3 className="font-bold text-gray-900 mb-1">비슷한 차량이 궁금하신가요?</h3>
                <p className="text-sm text-gray-600 mb-3">AI에게 추천을 요청해보세요</p>
                <Link
                  to={`/recommendation?query=${encodeURIComponent(`${clean(car.maker) ?? ''} ${clean(car.model) ?? ''} 비슷한 차량 추천`.trim())}`}
                  className="btn-primary text-sm"
                >
                  AI 추천 받기 →
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 하단 대형 광고 */}
      <AdSlot id="detail-bottom" variant="rectangle" />
    </div>
  )
}
