import React from 'react'

export default function VehicleIcon({ type, color, className = 'w-9 h-9' }: { type: string; color: string; className?: string }) {
  const isLight = color === '#E5E7EB'
  const stroke = isLight ? '#9CA3AF' : 'none'
  const sw = isLight ? 0.8 : 0
  const svgProps = { viewBox: '0 0 64 64', xmlns: 'http://www.w3.org/2000/svg', className, style: { color } }

  if (type === 'micro') return (
    <svg {...svgProps}>
      <polygon points="10,40 10,26 16,20 34,20 42,28 50,28 50,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="17,21 33,21 39,27 12,27" fill="white"/>
      <rect x="25" y="21" width="2" height="6" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="42" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  if (type === 'small') return (
    <svg {...svgProps}>
      <polygon points="6,40 6,28 16,20 38,20 48,28 54,28 54,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="17,21 36,21 44,27 8,27" fill="white"/>
      <rect x="28" y="21" width="2" height="6" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  if (type === 'compact') return (
    <svg {...svgProps}>
      <polygon points="4,40 4,30 16,20 36,20 46,28 58,28 58,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="17,21 35,21 42,27 8,29" fill="white"/>
      <rect x="26" y="21" width="2" height="7" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  if (type === 'midsize') return (
    <svg {...svgProps}>
      <polygon points="2,40 2,30 14,20 40,20 50,28 60,28 60,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="15,21 38,21 46,27 6,29" fill="white"/>
      <rect x="28" y="21" width="2" height="7" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  if (type === 'fullsize') return (
    <svg {...svgProps}>
      <polygon points="2,40 2,28 14,18 44,18 54,28 62,28 62,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="15,19 42,19 50,27 6,27" fill="white"/>
      <rect x="30" y="19" width="3" height="8" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  if (type === 'rv') return (
    <svg {...svgProps}>
      <polygon points="4,40 4,20 12,18 42,18 54,30 58,30 58,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="6,21 13,19 41,19 50,29 6,29" fill="white"/>
      <rect x="22" y="19" width="2" height="10" fill="white"/>
      <rect x="36" y="19" width="2" height="10" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  if (type === 'suv') return (
    <svg {...svgProps}>
      <polygon points="4,38 4,22 16,18 42,18 52,26 58,26 58,38" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="7,23 17,19 41,19 49,25 7,25" fill="white"/>
      <rect x="24" y="19" width="2" height="6" fill="white"/>
      <rect x="36" y="19" width="2" height="6" fill="white"/>
      <circle cx="16" cy="40" r="7" fill="#111827"/><circle cx="48" cy="40" r="7" fill="#111827"/>
    </svg>
  )
  if (type === 'sports') return (
    <svg {...svgProps}>
      <polygon points="6,40 6,32 22,22 36,22 48,30 60,30 60,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="21,23 35,23 44,29 13,31" fill="white"/>
      <rect x="28" y="23" width="2" height="7" fill="white"/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
  // cargo / truck
  return (
    <svg {...svgProps}>
      <polygon points="4,40 4,22 20,22 26,30 26,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <polygon points="6,29 6,23 19,23 23,29" fill="white"/>
      <polygon points="28,40 28,30 60,30 60,40" fill="currentColor" stroke={stroke} strokeWidth={sw}/>
      <circle cx="16" cy="40" r="6" fill="#111827"/><circle cx="48" cy="40" r="6" fill="#111827"/>
    </svg>
  )
}
