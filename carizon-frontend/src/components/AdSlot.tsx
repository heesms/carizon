import React, { useEffect, useRef, useState } from 'react'

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
  const containerRef = useRef<HTMLDivElement>(null)
  const [isLoaded, setIsLoaded] = useState(false)
  const [showPlaceholder, setShowPlaceholder] = useState(true)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const checkLoaded = () => {
      if (container.childElementCount > 0) {
        setIsLoaded(true)
      }
    }

    checkLoaded()
    if (isLoaded) return

    const hideTimer = window.setTimeout(() => {
      setShowPlaceholder(false)
    }, 1200)

    const mutationObserver = new MutationObserver(() => {
      if (container.childElementCount > 0) {
        setIsLoaded(true)
        setShowPlaceholder(false)
      }
    })
    mutationObserver.observe(container, { childList: true, subtree: true })

    return () => {
      clearTimeout(hideTimer)
      mutationObserver.disconnect()
    }
  }, [isLoaded])

  const isHidden = !isLoaded && !showPlaceholder

  const baseClass = isHidden
    ? 'opacity-0 min-h-0 h-0 max-h-0 overflow-hidden'
    : 'opacity-100 min-h-[var(--slot-min-height)]'

  const placeholder = !isLoaded && showPlaceholder ? (
    <div
      className="w-full h-full rounded bg-gradient-to-r from-gray-50 via-gray-100 to-gray-50 animate-pulse"
      aria-hidden="true"
    />
  ) : null

  return (
    <div
      ref={containerRef}
      id={id}
      role={isLoaded ? 'complementary' : undefined}
      aria-label={isLoaded ? `광고 슬롯 ${id}` : undefined}
      className={`relative w-full transition-all duration-500 ease-in-out ${baseClass} ${className}`}
      style={
        {
          '--slot-min-height': `${minHeights[variant]}px`,
        } as React.CSSProperties
      }
    >
      {placeholder}
    </div>
  )
}
