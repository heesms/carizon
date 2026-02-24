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
    setBusy(true)
    try {
      const next = await toggleLike(carId)
      setLiked(!!next?.liked)
      setCount(Number(next?.count ?? 0))
      trackLike(carId, !!next?.liked)
    } catch {
      // no-op
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
      <span>{liked ? '💙' : '🤍'}</span>
      <span>{count}</span>
    </button>
  )
}
