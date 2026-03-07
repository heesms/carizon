import React, { useEffect, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { getBodyTypes, type CodeItem } from '@/api/codes'
import SeoCarsSection from '@/components/SeoCarsSection'
import VehicleIcon from '@/components/VehicleIcon'
import { bodyTypeSlugToKr, bodyTypeSlugToEntry } from '@/utils/slugs'

const SITE_NAME = 'Carizon'

export default function BodyTypePage() {
  const { bodyTypeSlug = '' } = useParams<{ bodyTypeSlug: string }>()
  const navigate = useNavigate()
  const krValue = bodyTypeSlugToKr(bodyTypeSlug) ?? bodyTypeSlug
  const slugEntry = bodyTypeSlugToEntry(bodyTypeSlug)
  const [bodyTypeData, setBodyTypeData] = useState<CodeItem | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!krValue) return
    getBodyTypes()
      .then(types => {
        const found = types.find(t => t.code === krValue || t.name === krValue) ?? null
        setBodyTypeData(found)
      })
      .catch(() => setBodyTypeData(null))
      .finally(() => setLoading(false))
  }, [krValue])

  const displayName = bodyTypeData?.name ?? krValue
  const carCount = bodyTypeData?.carCount ?? 0
  const color = slugEntry?.color ?? '#6B7280'
  const desc = slugEntry?.desc ?? `${displayName} 중고차를 여러 플랫폼에서 통합 비교하세요.`

  // SEO 메타 동적 적용
  useEffect(() => {
    if (!displayName) return
    const title = `${displayName} 중고차 | ${SITE_NAME}`
    document.title = title
    const fullDesc = `${desc} 현재 ${carCount.toLocaleString()}대의 ${displayName} 매물이 등록되어 있습니다.`
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', fullDesc)
    document.querySelector<HTMLMetaElement>('meta[property="og:title"]')?.setAttribute('content', title)
    document.querySelector<HTMLMetaElement>('meta[property="og:description"]')?.setAttribute('content', fullDesc)

    const jsonLd = {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: '홈', item: 'https://www.carizon.shop/' },
        { '@type': 'ListItem', position: 2, name: `${displayName} 중고차`, item: `https://www.carizon.shop/cars/type/${bodyTypeSlug}` },
      ],
    }
    let script = document.querySelector<HTMLScriptElement>('script[type="application/ld+json"][data-seo-page]')
    if (!script) {
      script = document.createElement('script')
      script.type = 'application/ld+json'
      script.setAttribute('data-seo-page', '1')
      document.head.appendChild(script)
    }
    script.textContent = JSON.stringify(jsonLd)
    return () => {
      document.querySelector('script[type="application/ld+json"][data-seo-page]')?.remove()
    }
  }, [displayName, bodyTypeSlug, carCount, desc])

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="animate-pulse bg-gray-100 rounded-2xl h-40 mb-8" />
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* 뒤로가기 + 브레드크럼 */}
      <div className="flex items-center gap-2 mb-4">
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="뒤로가기"
          className="shrink-0 h-8 w-8 inline-flex items-center justify-center rounded-full bg-gray-100 hover:bg-gray-200 active:bg-gray-300 transition-colors"
        >
          <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <nav className="text-xs text-gray-400 flex items-center gap-1.5">
          <Link to="/" className="hover:text-blue-600">홈</Link>
          <span>›</span>
          <span className="text-gray-600 font-medium">{displayName} 중고차</span>
        </nav>
      </div>

      {/* ── 차종 배너 ── */}
      <div className="bg-gradient-to-br from-slate-50 to-green-50 rounded-2xl p-6 mb-8 border border-slate-100">
        <div className="flex items-center gap-4 mb-4">
          <div className="w-16 h-16 rounded-2xl bg-white shadow-sm flex items-center justify-center">
            <VehicleIcon type={bodyTypeSlug} color={color} className="w-10 h-10" />
          </div>
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">
              {displayName} 중고차
            </h1>
          </div>
        </div>

        <p className="text-gray-600 text-sm leading-relaxed mb-3">
          {desc}
        </p>
        {carCount > 0 && (
          <div className="flex items-center gap-3 text-sm text-gray-500">
            <span className="flex items-center gap-1">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              <strong>{carCount.toLocaleString()}</strong>대 매물
            </span>
            <span>·</span>
            <span>여러 플랫폼 통합 비교</span>
          </div>
        )}
      </div>

      {/* ── 차량 목록 ── */}
      <h2 className="text-lg font-semibold text-gray-800 mb-4">
        {displayName} 중고차 매물
      </h2>
      <SeoCarsSection fixedParams={{ bodyType: krValue }} />
    </div>
  )
}
