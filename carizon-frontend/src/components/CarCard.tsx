import React, { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { CarListItem } from '@/api/cars'
import { getLike, toggleLike } from '@/api/likes'
import { trackLike, trackSelectItem } from '@/lib/analytics'
import CarImagePlaceholder from '@/components/CarImagePlaceholder'

function getImgSrc(item: CarListItem): string | null {
  if (item.representativeImageUrl?.trim()) return item.representativeImageUrl
  if (item.modelCode) return `/image/car/model/${item.modelCode}.webp`
  return null
}

function formatPrice(min?: number, max?: number) {
  if (min == null && max == null) return null
  if (min != null && max != null && min !== max)
    return `${min.toLocaleString()} ~ ${max.toLocaleString()}만원`
  return `${(min ?? max)!.toLocaleString()}만원`
}

const cleanText = (v?: string | null) => {
  if (typeof v !== 'string') return ''
  const t = v.trim()
  if (!t || t.toLowerCase() === 'null') return ''
  return t
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

type Props = {
  item: CarListItem
  likesCount?: number
  showLikeCount?: boolean
  loadLikeOnMount?: boolean
  initialLiked?: boolean
  onConfirmUnlike?: (carId: number) => boolean | Promise<boolean>
  onLikeChanged?: (carId: number, liked: boolean, count: number) => void
}

export default function CarCard({
  item,
  likesCount,
  showLikeCount = true,
  loadLikeOnMount = true,
  initialLiked = false,
  onConfirmUnlike,
  onLikeChanged,
}: Props) {
  const location = useLocation()
  const [src, setSrc] = useState<string | null>(getImgSrc(item))
  const [liked, setLiked] = useState(!!initialLiked)
  const [likeCount, setLikeCount] = useState(Number(likesCount ?? 0))
  const [likeBusy, setLikeBusy] = useState(false)
  const price = formatPrice(item.priceMin, item.priceMax)
  if (!price) return null

  useEffect(() => {
    setLikeCount(Number(likesCount ?? 0))
  }, [likesCount])

  useEffect(() => {
    setLiked(!!initialLiked)
  }, [initialLiked, item.carId])

  useEffect(() => {
    if (!loadLikeOnMount) return
    let mounted = true
    getLike(item.carId)
      .then((data) => {
        if (!mounted) return
        const nextLiked = !!data?.liked
        const nextCount = Number(data?.count ?? likesCount ?? 0)
        setLiked(nextLiked)
        setLikeCount(nextCount)
      })
      .catch(() => {
        // no-op
      })
    return () => { mounted = false }
  }, [item.carId, likesCount, loadLikeOnMount])

  const onToggleLike = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault()
    e.stopPropagation()
    if (likeBusy) return
    const prevLiked = liked
    const prevCount = likeCount
    const optimisticLiked = !prevLiked
    if (!optimisticLiked && onConfirmUnlike) {
      try {
        const confirmed = await Promise.resolve(onConfirmUnlike(item.carId))
        if (!confirmed) return
      } catch {
        return
      }
    }

    const optimisticCount = Math.max(0, prevCount + (optimisticLiked ? 1 : -1))
    setLiked(optimisticLiked)
    setLikeCount(optimisticCount)
    onLikeChanged?.(item.carId, optimisticLiked, optimisticCount)

    setLikeBusy(true)
    try {
      const next = await toggleLike(item.carId)
      const nextLiked = !!next?.liked
      const nextCountRaw = Number(next?.count)
      const nextCount = Number.isFinite(nextCountRaw) ? nextCountRaw : optimisticCount
      setLiked(nextLiked)
      setLikeCount(nextCount)
      onLikeChanged?.(item.carId, nextLiked, nextCount)
      trackLike(item.carId, nextLiked)
    } catch {
      setLiked(prevLiked)
      setLikeCount(prevCount)
      onLikeChanged?.(item.carId, prevLiked, prevCount)
    } finally {
      setLikeBusy(false)
    }
  }

  const from = `${location.pathname}${location.search}${location.hash}`
  const source = location.pathname.startsWith('/recommendation')
    ? 'ai'
    : location.pathname.startsWith('/search')
      ? 'search'
      : 'other'
  const maker = cleanText(item.maker)
  const model = cleanText(item.model)
  const trim = cleanText(item.trim)
  // 검색 결과에서는 페이지 전환, AI 추천에서는 모달
  const state = source === 'search'
    ? { from, source }
    : { from, source, backgroundLocation: location }

  return (
    <Link
      to={`/cars/${item.carId}`}
      state={state}
      className="card-hover flex flex-col group overflow-hidden h-full"
      onClick={() => {
        if (source === 'search') {
          sessionStorage.setItem('search_scroll_y', JSON.stringify({
            url: `${location.pathname}${location.search}`,
            y: window.scrollY,
          }))
        }
        trackSelectItem(item.carId, maker, model, source)
      }}
    >
      {/* 이미지 */}
      <div className="relative aspect-[4/3] overflow-hidden bg-gray-50">
        {src ? (
          <img
            src={src}
            alt={`${maker} ${model}`.trim()}
            className="w-full h-full object-cover object-center scale-[1.15] group-hover:scale-[1.25] transition-transform duration-300"
            loading="lazy"
            onError={() => setSrc(null)}
          />
        ) : (
          <CarImagePlaceholder />
        )}
      </div>

      {/* 정보 */}
      <div className="p-3 flex flex-col gap-1.5">
        {/* 차명 */}
        <h3 className="font-bold text-sm leading-snug text-gray-900 group-hover:text-brand-600 transition-colors line-clamp-1">
          {maker} {model}
          {trim ? <span className="font-normal text-gray-500 ml-1 text-xs">{trim}</span> : null}
        </h3>

        {/* 스펙 태그 */}
        <div className="flex flex-wrap gap-1 content-start">
          {item.year    && <span className="badge badge-gray text-[11px]">{item.year}년식</span>}
          {item.km != null && item.km > 0 && <span className="badge badge-gray text-[11px]">{item.km.toLocaleString()}km</span>}
          {item.fuel    && <span className={`badge ${fuelBadge(item.fuel)} text-[11px]`}>{item.fuel}</span>}
          {item.region  && <span className="badge badge-gray text-[11px]">{item.region.split(' ').slice(0, 2).join(' ')}</span>}
        </div>

        {/* 가격 + 좋아요 */}
        <div className="flex items-center justify-between">
          <span className="text-lg font-black text-brand-600">{price}</span>
          <button
            type="button"
            onClick={onToggleLike}
            disabled={likeBusy}
            className={`inline-flex items-center justify-center text-xs rounded-md w-7 h-7 transition ${
              liked ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-400 hover:bg-red-50'
            } ${likeBusy ? 'opacity-60 cursor-wait' : ''}`}
            aria-label={liked ? '좋아요 취소' : '좋아요'}
          >
            <svg className="w-3.5 h-3.5" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
            </svg>
            {showLikeCount && <span className="ml-1 text-[11px] leading-none">{likeCount}</span>}
          </button>
        </div>
      </div>
    </Link>
  )
}
