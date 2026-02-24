import React, { useEffect, useMemo, useRef, useState } from 'react'
import CarCard from '@/components/CarCard'
import { getMyLikedCars, type MyLikedCar } from '@/api/likes'

export default function MyLikes() {
  const [items, setItems] = useState<MyLikedCar[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [confirmCarId, setConfirmCarId] = useState<number | null>(null)
  const confirmResolveRef = useRef<((ok: boolean) => void) | null>(null)

  const confirmTarget = useMemo(
    () => items.find(item => item.carId === confirmCarId) ?? null,
    [items, confirmCarId]
  )

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    getMyLikedCars(200)
      .then((rows) => {
        if (cancelled) return
        setItems(Array.isArray(rows) ? rows : [])
      })
      .catch(() => {
        if (cancelled) return
        setItems([])
        setError('찜한 차량을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
      })
      .finally(() => {
        if (cancelled) return
        setLoading(false)
      })

    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    return () => {
      if (confirmResolveRef.current) {
        confirmResolveRef.current(false)
        confirmResolveRef.current = null
      }
    }
  }, [])

  const askUnlikeConfirm = (carId: number) => new Promise<boolean>((resolve) => {
    confirmResolveRef.current = resolve
    setConfirmCarId(carId)
  })

  const closeConfirm = (ok: boolean) => {
    if (confirmResolveRef.current) {
      confirmResolveRef.current(ok)
      confirmResolveRef.current = null
    }
    setConfirmCarId(null)
  }

  return (
    <div className="space-y-5 animate-fade-in">
      <div>
        <h1 className="text-xl font-black text-gray-900">찜한 매물</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          총 <strong className="text-gray-900">{items.length.toLocaleString()}</strong>개 매물
        </p>
      </div>

      {loading && (
        <div className="card p-8 text-center text-gray-500">
          <div className="spinner mx-auto mb-3" />
          찜한 차량을 불러오는 중입니다.
        </div>
      )}

      {!loading && error && (
        <div className="card p-5 border border-red-100 bg-red-50 text-sm text-red-600">
          {error}
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="card p-10 text-center">
          <div className="text-5xl mb-3">🤍</div>
          <p className="text-gray-600 font-medium mb-1">아직 찜한 차량이 없습니다</p>
          <p className="text-sm text-gray-400">검색 결과에서 하트를 눌러 관심 매물을 저장해 보세요.</p>
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {items.map((item, idx) => (
            <div key={item.carId} className="animate-fade-in" style={{ animationDelay: `${idx * 0.03}s` }}>
              <CarCard
                item={item}
                likesCount={Number(item.likesCount ?? 0)}
                showLikeCount={false}
                loadLikeOnMount={false}
                initialLiked={true}
                onConfirmUnlike={askUnlikeConfirm}
                onLikeChanged={(carId, liked, count) => {
                  setItems(prev => {
                    if (!liked) return prev.filter(row => row.carId !== carId)
                    return prev.map(row => row.carId === carId ? { ...row, likesCount: count } : row)
                  })
                }}
              />
            </div>
          ))}
        </div>
      )}

      {confirmCarId != null && (
        <div
          className="fixed inset-0 z-50 bg-black/40 backdrop-blur-[2px] flex items-center justify-center px-4"
          onClick={() => closeConfirm(false)}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-white shadow-2xl border border-gray-100 p-5 animate-fade-in"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="w-10 h-10 rounded-full bg-red-50 text-red-500 flex items-center justify-center mx-auto mb-3">
              <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6.01 6.01 0 0116.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
              </svg>
            </div>
            <h3 className="text-center text-base font-bold text-gray-900">찜한 차량에서 제거할까요?</h3>
            <p className="text-center text-sm text-gray-500 mt-2 line-clamp-2">
              {confirmTarget ? `${confirmTarget.maker ?? ''} ${confirmTarget.model ?? ''} ${confirmTarget.trim ?? ''}`.trim() : '선택한 차량'}
            </p>
            <div className="mt-5 grid grid-cols-2 gap-2">
              <button
                type="button"
                className="h-10 rounded-xl border border-gray-200 text-gray-600 font-semibold hover:bg-gray-50 transition"
                onClick={() => closeConfirm(false)}
              >
                취소
              </button>
              <button
                type="button"
                className="h-10 rounded-xl bg-red-500 text-white font-semibold hover:bg-red-600 transition"
                onClick={() => closeConfirm(true)}
              >
                제거
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
