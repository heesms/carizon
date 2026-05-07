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
  const placeholderRef = useRef<HTMLDivElement>(null)
  const [isLoaded, setIsLoaded] = useState(false)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const checkLoaded = () => {
      const hasExternalChildren = Array.from(container.children).some(
        (child) => child !== placeholderRef.current,
      )
      if (hasExternalChildren) {
        setIsLoaded(true)
      }
    }

    checkLoaded()
    if (isLoaded) return undefined

    const mutationObserver = new MutationObserver(() => {
      const hasExternalChildren = Array.from(container.children).some(
        (child) => child !== placeholderRef.current,
      )
      if (hasExternalChildren) {
        setIsLoaded(true)
      }
    })
    mutationObserver.observe(container, { childList: true, subtree: true })

    return () => {
      mutationObserver.disconnect()
    }
  }, [isLoaded])

  return (
    <div
      ref={containerRef}
      id={id}
      role={isLoaded ? 'complementary' : undefined}
      aria-label={isLoaded ? `광고 슬롯 ${id}` : undefined}
      className={`relative w-full min-h-[var(--slot-min-height)] ${className}`}
      style={
        {
          '--slot-min-height': `${minHeights[variant]}px`,
        } as React.CSSProperties
      }
    >
      {!isLoaded && (
        <div
          ref={placeholderRef}
          className="absolute inset-0 grid place-items-center rounded border border-dashed border-gray-200 bg-gray-50 text-xs font-medium tracking-[0.18em] text-gray-400"
          aria-hidden="true"
        >
          AD: {id}
        </div>
      )}
    </div>
  )
}
