import React from 'react'
import { type PricePoint as ApiPricePoint } from '@/api/cars'

type PricePoint = ApiPricePoint & { ts?: string }

export default function PriceChart({ points, width = 600, height = 220 }: { points: PricePoint[]; width?: number; height?: number }) {
  if (!points || points.length === 0) return <div className="text-sm text-gray-500">가격 이력이 없습니다.</div>

  const normalized = points
    .map(p => {
      const ts = p.ts ?? p.checkedAt
      return ts ? { ts, price: p.price } : null
    })
    .filter((it): it is { ts: string; price: number } => !!it)

  if (normalized.length === 0) {
    return <div className="text-sm text-gray-500">가격 이력 형식이 올바르지 않습니다.</div>
  }

  const toTime = (ts: string) => +new Date(ts)

  const pad = { l: 40, r: 10, t: 10, b: 20 }
  const xs = normalized.map(p => toTime(p.ts))
  const ys = normalized.map(p => p.price)

  if (xs.some(v => Number.isNaN(v))) {
    return <div className="text-sm text-gray-500">가격 이력 형식이 올바르지 않습니다.</div>
  }

  const xmin = Math.min(...xs)
  const xmax = Math.max(...xs)
  const ymin = Math.min(...ys)
  const ymax = Math.max(...ys)

  const X = (t: number) => pad.l + ((t - xmin) / Math.max(1, xmax - xmin)) * (width - pad.l - pad.r)
  const Y = (v: number) => pad.t + (1 - ((v - ymin) / Math.max(1, ymax - ymin))) * (height - pad.t - pad.b)
  const d = normalized.map((p, i) => `${i ? 'L' : 'M'} ${X(toTime(p.ts)).toFixed(1)} ${Y(p.price).toFixed(1)}`).join(' ')
  const yTicks = [ymin, Math.round((ymin + ymax) / 2), ymax]
  const xTicks = [xmin, xmax]

  return (
    <svg width={width} height={height} className="bg-white border rounded">
      <g stroke="#e5e7eb">
        <line x1={pad.l} y1={height - pad.b} x2={width - pad.r} y2={height - pad.b} />
        <line x1={pad.l} y1={pad.t} x2={pad.l} y2={height - pad.b} />
        {yTicks.map((v, i) => (
          <g key={i}>
            <line x1={pad.l} y1={Y(v)} x2={width - pad.r} y2={Y(v)} />
            <text x={4} y={Y(v) - 2} fontSize="10" fill="#6b7280">{v.toLocaleString()}</text>
          </g>
        ))}
        {xTicks.map((t, i) => (
          <text key={i} x={X(t)} y={height - 4} fontSize="10" textAnchor="middle" fill="#6b7280">
            {new Date(t).toLocaleDateString()}
          </text>
        ))}
      </g>
      <path d={d} fill="none" stroke="#111827" strokeWidth="2" />
      {normalized.map((p, i) => (
        <circle key={i} cx={X(toTime(p.ts))} cy={Y(p.price)} r="3" fill="#111827" />
      ))}
    </svg>
  )
}
