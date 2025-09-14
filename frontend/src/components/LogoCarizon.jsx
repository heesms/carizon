import React from 'react'

export default function LogoCarizon({ size=28 }) {
    // Car + Horizon 모티프 SVG (단색, 가벼움)
    return (
        <svg width={size*5} height={size*1.4} viewBox="0 0 220 60" role="img" aria-label="Carizon">
            {/* horizon sun */}
            <circle cx="42" cy="28" r="12" fill="#f59e0b" opacity="0.8"/>
            {/* horizon line */}
            <rect x="10" y="40" width="80" height="4" rx="2" fill="#3b82f6"/>
            {/* car silhouette */}
            <path d="M18 38c2-8 10-14 22-14 12 0 21 6 24 14h-4c-2-5-8-10-20-10s-18 5-20 10h-2z" fill="#0ea5e9"/>
            {/* wordmark */}
            <text x="100" y="42" fontFamily="Inter, ui-sans-serif" fontSize="28" fontWeight="700" fill="#0f172a">Carizon</text>
        </svg>
    )
}
