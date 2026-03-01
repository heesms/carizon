import React, { useEffect, useState } from 'react'
import { getLike, toggleLike } from '@/api/likes'
import { trackLike } from '@/lib/analytics'

type Props = {
  carId: number
  className?: string
}

export default function LikeButton({ carId, className = '' }: Props) {
  const [liked, setLiked] = useState(false)
  const [count, setCount] = useState(0)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let mounted = true
    getLike(carId)
      .then(data => {
        if (!mounted) return
        setLiked(!!data?.liked)
        setCount(Number(data?.count ?? 0))
      })
      .catch(() => {
        // no-op
      })
    return () => { mounted = false }
  }, [carId])

  const onToggle = async () => {
    if (busy) return
    const prevLiked = liked
    const prevCount = count
    const optimisticLiked = !prevLiked
    const optimisticCount = Math.max(0, prevCount + (optimisticLiked ? 1 : -1))

    setLiked(optimisticLiked)
    setCount(optimisticCount)
    setBusy(true)

    try {
      const next = await toggleLike(carId)
      if (next) {
        setLiked(!!next.liked)
        const nextCount = Number(next.count)
        setCount(Number.isFinite(nextCount) ? nextCount : optimisticCount)
        trackLike(carId, !!next.liked)
      } else {
        setLiked(optimisticLiked)
        setCount(optimisticCount)
      }
    } catch {
      try {
        const refreshed = await getLike(carId)
        setLiked(!!refreshed?.liked)
        const refreshedCount = Number(refreshed?.count ?? NaN)
        setCount(Number.isFinite(refreshedCount) ? refreshedCount : prevCount)
      } catch {
        setLiked(prevLiked)
        setCount(prevCount)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={busy}
      className={`btn-secondary inline-flex items-center gap-2 ${className} ${busy ? 'opacity-60 cursor-wait' : ''}`}
      aria-label={liked ? '좋아요 취소' : '좋아요'}
    >
      <svg className={`w-5 h-5 ${liked ? 'text-red-500' : 'text-gray-400'}`} fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
      </svg>
      <span>{count}</span>
    </button>
  )
}
