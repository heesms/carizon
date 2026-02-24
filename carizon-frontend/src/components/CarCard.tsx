import React, { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { CarListItem } from '@/api/cars'
import { trackSelectItem } from '@/lib/analytics'

const NO_IMAGE = `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300"><rect fill="#f3f4f6" width="400" height="300"/><text fill="#9ca3af" font-family="sans-serif" font-size="13" x="200" y="158" text-anchor="middle">이미지 없음</text><rect fill="#e5e7eb" x="170" y="110" width="60" height="38" rx="4"/></svg>')}`

function imgSrc(item: CarListItem) {
  if (item.representativeImageUrl?.trim()) return item.representativeImageUrl
  if (item.modelCode) return `/image/car/model/${item.modelCode}.webp`
  return NO_IMAGE
}

function formatPrice(min?: number, max?: number) {
  if (min == null && max == null) return null
  if (min != null && max != null && min !== max)
    return `${min.toLocaleString()} ~ ${max.toLocaleString()}만원`
  return `${(min ?? max)!.toLocaleString()}만원`
}

// 연료별 배지 색
function fuelBadge(fuel?: string) {
  if (!fuel) return 'badge-gray'
  const f = fuel.toLowerCase()
  if (f.includes('전기'))    return 'badge-blue'
  if (f.includes('하이브리드')) return 'badge-green'
  if (f.includes('디젤'))    return 'badge-amber'
  return 'badge-gray'
}

type Props = { item: CarListItem; likesCount?: number }

export default function CarCard({ item, likesCount }: Props) {
  const location = useLocation()
  const [src, setSrc] = useState(imgSrc(item))
  const price = formatPrice(item.priceMin, item.priceMax)
  if (!price) return null

  const from = `${location.pathname}${location.search}${location.hash}`
  const source = location.pathname.startsWith('/recommendation')
    ? 'ai'
    : location.pathname.startsWith('/search')
      ? 'search'
      : 'other'
  // 검색 결과에서는 페이지 전환, AI 추천에서는 모달
  const state = source === 'search'
    ? { from, source }
    : { from, source, backgroundLocation: location }

  return (
    <Link
      to={`/cars/${item.carId}`}
      state={state}
      className="card-hover flex flex-col group overflow-hidden"
      onClick={() => trackSelectItem(item.carId, item.maker, item.model, source)}
    >
      {/* 이미지 */}
      <div className="relative aspect-[4/3] overflow-hidden bg-gray-50">
        <img
          src={src}
          alt={`${item.maker} ${item.model}`}
          className="w-full h-full object-cover object-center scale-[1.15] group-hover:scale-[1.25] transition-transform duration-300"
          loading="lazy"
          onError={() => { if (src !== NO_IMAGE) setSrc(NO_IMAGE) }}
        />
      </div>

      {/* 정보 */}
      <div className="p-3.5 flex flex-col gap-2">
        {/* 차명 */}
        <h3 className="font-bold text-sm leading-snug text-gray-900 group-hover:text-brand-600 transition-colors line-clamp-1">
          {item.maker} {item.model}
          {item.trim ? <span className="font-normal text-gray-500 ml-1 text-xs">{item.trim}</span> : null}
        </h3>

        {/* 스펙 태그 */}
        <div className="flex flex-wrap gap-1">
          {item.year    && <span className="badge badge-gray text-[11px]">{item.year}년식</span>}
          {item.km != null && item.km > 0 && <span className="badge badge-gray text-[11px]">{item.km.toLocaleString()}km</span>}
          {item.fuel    && <span className={`badge ${fuelBadge(item.fuel)} text-[11px]`}>{item.fuel}</span>}
          {item.region  && <span className="badge badge-gray text-[11px]">{item.region}</span>}
        </div>

        {/* 가격 + 좋아요 */}
        <div className="flex items-end justify-between mt-auto pt-1">
          <span className="text-lg font-black text-brand-600">{price}</span>
          {likesCount != null && (
            <span className="flex items-center gap-1 text-xs text-gray-400">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
              </svg>
              {likesCount}
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}
