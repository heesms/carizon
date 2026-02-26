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
  encar_truck: '엔카', ENCAR_TRUCK: '엔카',
  kcar: 'K Car', KCAR: 'K Car',
  chachacha: 'KB차차차', CHACHACHA: 'KB차차차',
  chutcha: '첫차', CHUTCHA: '첫차',
  charancha: '차란차', CHARANCHA: '차란차',
  tcar: 'T Car', TCAR: 'T Car',
}

const normalizePlatformKey = (platformName: string) => {
  const key = String(platformName ?? '').toUpperCase()
  return key === 'ENCAR_TRUCK' ? 'ENCAR' : key
}

const platformLabel = (p: string) => {
  const normalized = normalizePlatformKey(p)
  return PLATFORM_NAMES[p] ?? PLATFORM_NAMES[normalized] ?? p
}

// 플랫폼 바 차트 색상 (bg / text)
const PLATFORM_BAR_COLORS: Record<string, { bg: string; text: string; light: string }> = {
  ENCAR:     { bg: 'bg-red-500',    text: 'text-red-700',    light: 'bg-red-50' },
  KCAR:      { bg: 'bg-blue-500',   text: 'text-blue-700',   light: 'bg-blue-50' },
  CHACHACHA: { bg: 'bg-yellow-500', text: 'text-yellow-700', light: 'bg-yellow-50' },
  CHUTCHA:   { bg: 'bg-green-500',  text: 'text-green-700',  light: 'bg-green-50' },
  CHARANCHA: { bg: 'bg-purple-500', text: 'text-purple-700', light: 'bg-purple-50' },
  TCAR:      { bg: 'bg-orange-500', text: 'text-orange-700', light: 'bg-orange-50' },
}
const barColor = (p: string) =>
  PLATFORM_BAR_COLORS[normalizePlatformKey(p)] ?? { bg: 'bg-gray-400', text: 'text-gray-700', light: 'bg-gray-50' }

const normalizeOptionText = (value: string) =>
  value.toLowerCase().replace(/[^0-9a-z가-힣]/g, '')

const KEY_OPTION_RULES: Array<{ id: string; label: string; keywords: string[]; iconSrc: string }> = [
  { id: 'sunroof', label: '선루프', keywords: ['선루프', '썬루프'], iconSrc: '/icons/options/1.png' },
  { id: 'heated-seat', label: '열선시트', keywords: ['열선시트'], iconSrc: '/icons/options/3.png' },
  { id: 'vent-seat', label: '통풍시트', keywords: ['통풍시트'], iconSrc: '/icons/options/4.png' },
  { id: 'leather-seat', label: '가죽시트', keywords: ['가죽시트'], iconSrc: '/icons/options/5.png' },
  { id: 'power-seat', label: '전동시트', keywords: ['전동시트'], iconSrc: '/icons/options/12.png' },
  { id: 'parking-sensor', label: '주차센서', keywords: ['주차감지', '주차감지센서', '주차센서'], iconSrc: '/icons/options/6.png' },
  { id: 'heated-wheel', label: '핸들 열선', keywords: ['열선 스티어링', '열선스티어링', '열선핸들'], iconSrc: '/icons/options/7.png' },
  { id: 'blind-spot', label: '후측방경고', keywords: ['후측방', '후측방경보', '후측방경고'], iconSrc: '/icons/options/8.png' },
  { id: 'lane-departure', label: '차선이탈경보', keywords: ['차선이탈 경보', '차선이탈경보'], iconSrc: '/icons/options/9.png' },
  { id: 'hud', label: 'HUD', keywords: ['헤드업 디스플레이', '헤드업디스플레이', 'hud'], iconSrc: '/icons/options/10.png' },
  { id: 'around-view', label: '어라운드뷰', keywords: ['어라운드', '어라운드뷰'], iconSrc: '/icons/options/2.png' },
  { id: 'auto-aircon', label: '자동에어컨', keywords: ['풀오토에어컨', '자동에어컨', '오토에어컨'], iconSrc: '/icons/options/11.png' },
]

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
    if (source === 'search') {
      const target = from || '/search'
      navigate(target, { state: { restoreSearchScrollFromDetail: true } })
      return
    }
    if (from) { navigate(from); return }
    navigate('/search')
  }

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
    if (!isModal) window.scrollTo(0, 0)
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
  const optionArrayValues = platforms
    .map(p => clean(p.optionArray))
    .filter((value): value is string => !!value)
  const hasOptionArrayData = optionArrayValues.length > 0
  const normalizedOptionSource = normalizeOptionText(optionArrayValues.join('|'))
  const keyOptions = KEY_OPTION_RULES.map((rule) => ({
    ...rule,
    enabled: rule.keywords.some(keyword =>
      normalizedOptionSource.includes(normalizeOptionText(keyword))
    ),
  }))

  const specs = [
    { label: '연식',     value: car.year ? `${car.year}년식` : undefined,
      bg: 'bg-blue-50',   ic: 'text-blue-500',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /> },
    { label: '주행거리', value: car.mileage ? `${car.mileage.toLocaleString()}km` : undefined,
      bg: 'bg-green-50',  ic: 'text-green-600',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M13 10V3L4 14h7v7l9-11h-7z" /> },
    { label: '배기량',   value: car.displacement ? `${car.displacement.toLocaleString()}cc` : undefined,
      bg: 'bg-orange-50', ic: 'text-orange-500',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" /> },
    { label: '연료',     value: clean(car.fuel),
      bg: 'bg-yellow-50', ic: 'text-yellow-600',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 5 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 13a7.975 7.975 0 01-2.343 5.657z" /> },
    { label: '변속기',   value: clean(car.transmission),
      bg: 'bg-purple-50', ic: 'text-purple-500',
      icon: <><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></> },
    { label: '차체',     value: clean(car.bodyType),
      bg: 'bg-brand-50',  ic: 'text-brand-600',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 17a2 2 0 11-4 0 2 2 0 014 0zM19 17a2 2 0 11-4 0 2 2 0 014 0zM13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0" /> },
    { label: '인승',     value: car.seatCount != null ? `${car.seatCount}인승` : undefined,
      bg: 'bg-teal-50',   ic: 'text-teal-600',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" /> },
    { label: '색상',     value: clean(car.color),
      bg: 'bg-pink-50',   ic: 'text-pink-500',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" /> },
    { label: '지역',     value: clean(car.region)?.split(' ').slice(0, 2).join(' '),
      bg: 'bg-red-50',    ic: 'text-red-500',
      icon: <><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" /></> },
    { label: '차량번호', value: clean(car.carNo),
      bg: 'bg-gray-100',  ic: 'text-gray-500',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M7 20l4-16m2 16l4-16M6 9h14M4 15h14" /> },
    { label: '신차가',   value: car.priceNew ? `${car.priceNew.toLocaleString()}만원` : undefined,
      bg: 'bg-amber-50',  ic: 'text-amber-600',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" /> },
  ].filter(s => s.value)

  const heroLabels = ['연식', '주행거리', '연료', '배기량']
  const heroSpecs = specs.filter(s => heroLabels.includes(s.label))
  const tableSpecs = specs.filter(s => !heroLabels.includes(s.label))

  return (
    <div className={`space-y-4 sm:space-y-6 animate-fade-in ${isModal ? 'px-3 sm:px-6 lg:px-7 pb-8' : ''}`}>
      {modalHeader}

      {/* PC 비모달 뒤로가기 breadcrumb */}
      {!isModal && (
        <div className="hidden lg:flex items-center gap-3">
          <button
            type="button"
            onClick={handleClose}
            className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-900 transition-colors group"
          >
            <svg className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            목록으로
          </button>
          <span className="text-gray-200 select-none">|</span>
          <span className="text-sm font-semibold text-gray-700 truncate">
            {[clean(car.maker), clean(car.model)].filter(Boolean).join(' ')}
            {clean(car.trim) && <span className="text-gray-400 font-normal ml-1">{car.trim}</span>}
          </span>
        </div>
      )}

      <div className={`grid grid-cols-1 gap-4 sm:gap-6 ${isModal ? 'md:grid-cols-[360px_minmax(0,1fr)] xl:grid-cols-[400px_minmax(0,1fr)]' : 'lg:grid-cols-[420px_1fr] lg:gap-8'}`}>
        {/* 좌측: 이미지 + 가격 */}
        <div className="space-y-4 lg:sticky lg:top-6 lg:self-start">
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

              {/* 가격 + 좋아요 */}
              {minPrice && (
                <div className="flex items-end justify-between gap-2 mb-1">
                  <div>
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
                  <div className="shrink-0 pb-0.5">
                    <LikeButton carId={Number(id)} />
                  </div>
                </div>
              )}

              {/* PC 전용 핵심 스펙 블록 */}
              {heroSpecs.length > 0 && (
                <div className="hidden lg:grid grid-cols-4 gap-2 mt-3 pt-3 border-t border-gray-100">
                  {heroSpecs.map(s => (
                    <div key={s.label} className={`rounded-xl p-2.5 text-center ${s.bg}`}>
                      <div className={`text-sm font-black ${s.ic} leading-tight`}>{s.value}</div>
                      <div className="text-[10px] text-gray-500 mt-0.5">{s.label}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

        </div>

        {/* 우측: 상세 정보 */}
        <div className="space-y-4">

          {/* 1. 플랫폼별 가격 비교 */}
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

          {/* 2. 광고 */}
          <AdSlot id="detail-side" variant="leaderboard" />

          {/* 3. 기본 스펙 */}
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
                  <path d="M8 18v-3a4 4 0 018 0v3Z" fill="currentColor" fillOpacity="0.24" stroke="none" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M8 18v-3a4 4 0 018 0v3M7 18h10M12 4v2M5 12h2M17 12h2M7 8.5l1.5 1.5M17 8.5l-1.5 1.5" />
                </svg>
                <p className="text-xs text-red-700 font-semibold">
                  내 차 사고이력 의심 - 구매 전 수리 이력을 꼭 확인하세요
                </p>
              </div>
            )}
            {/* 핵심 4개 hero stats */}
            {heroSpecs.length > 0 && (
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-4 lg:hidden">
                {heroSpecs.map(s => (
                  <div key={s.label} className={`rounded-xl p-3 sm:p-4 text-center ${s.bg}`}>
                    <div className={`text-base sm:text-xl font-black ${s.ic} leading-tight mb-0.5`}>{s.value}</div>
                    <div className="text-[11px] text-gray-500">{s.label}</div>
                  </div>
                ))}
              </div>
            )}
            {/* 나머지 스펙 테이블 */}
            {tableSpecs.length > 0 && (
              <dl className="grid grid-cols-2 gap-x-6">
                {tableSpecs.map(s => (
                  <div key={s.label} className="flex items-center justify-between py-2 border-b border-gray-100">
                    <dt className="text-xs text-gray-400 shrink-0">{s.label}</dt>
                    <dd className="text-xs font-semibold text-gray-800 text-right ml-2 truncate">{s.value}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>

          {/* 4. 주요 옵션 정보 */}
          {hasOptionArrayData && (
            <div className="card p-4 sm:p-5">
              <div className="mb-3">
                <h2 className="font-bold text-gray-900">주요 옵션정보</h2>
              </div>
              <div className="grid grid-cols-3 lg:grid-cols-6 gap-x-2 gap-y-4 sm:gap-y-5">
                {keyOptions.map((option) => (
                  <div
                    key={option.id}
                    className="flex flex-col items-center justify-start text-center min-h-[68px]"
                  >
                    <div className="h-8 flex items-center justify-center">
                      <img
                        src={option.iconSrc}
                        alt={option.label}
                        className={`w-7 h-7 object-contain select-none ${
                          option.enabled ? 'opacity-100' : 'opacity-25 grayscale'
                        }`}
                        loading="lazy"
                        draggable={false}
                      />
                    </div>
                    <span className={`mt-1.5 text-[11px] sm:text-xs font-semibold leading-tight ${
                      option.enabled ? 'text-gray-900' : 'text-gray-300'
                    }`}>
                      {option.label}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 5. 가격 히스토리 차트 */}
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
