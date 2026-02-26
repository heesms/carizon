import React from 'react'

type AdVariant = 'banner' | 'leaderboard' | 'rectangle'

const minHeights: Record<AdVariant, number> = {
  banner: 80,
  leaderboard: 90,
  rectangle: 220,
}

type Props = {
  id: string
  className?: string
  variant?: AdVariant
}

export default function AdSlot({ id, className = '', variant = 'banner' }: Props) {
  return (
    <div
      className={`border rounded bg-gray-100 text-gray-500 text-xs grid place-items-center ${className}`}
      style={{ minHeight: minHeights[variant] }}
    >
      AD: {id}
    </div>
  )
}
