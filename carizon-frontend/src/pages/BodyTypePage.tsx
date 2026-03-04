import React, { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getBodyTypes, type CodeItem } from '@/api/codes'
import SeoCarsSection from '@/components/SeoCarsSection'

const SITE_NAME = 'Carizon'

// 차종별 아이콘 이모지 매핑
const BODY_TYPE_ICON: Record<string, string> = {
  'SUV': '🚙',
  '세단': '🚗',
  'RV': '🚐',
  '미니밴': '🚐',
  '해치백': '🚗',
  '쿠페': '🏎️',
  '컨버터블': '🏎️',
  '픽업트럭': '🚚',
  '트럭': '🚚',
  '버스': '🚌',
  '전기차': '⚡',
}

// 차종별 설명
const BODY_TYPE_DESC: Record<string, string> = {
  'SUV': '넉넉한 공간과 높은 시야로 패밀리카로 인기 있는 SUV 중고차를 비교해 보세요.',
  '세단': '안정적인 주행 성능과 우아한 디자인의 세단 중고차를 한눈에 비교하세요.',
  'RV': '다목적 공간 활용이 뛰어난 RV 중고차 매물을 통합 비교하세요.',
  '미니밴': '대가족을 위한 넓은 공간의 미니밴 중고차를 비교해 보세요.',
  '해치백': '도심 주행에 최적화된 실용적인 해치백 중고차를 확인하세요.',
  '쿠페': '스포티한 디자인과 역동적인 주행의 쿠페 중고차를 비교하세요.',
  '컨버터블': '오픈 에어 드라이빙을 즐길 수 있는 컨버터블 중고차입니다.',
  '픽업트럭': '작업성과 실용성을 갖춘 픽업트럭 중고차를 비교하세요.',
  '전기차': '친환경 미래를 위한 전기차 중고차를 통합 비교하세요.',
}

export default function BodyTypePage() {
  const { bodyType = '' } = useParams<{ bodyType: string }>()
  const [bodyTypeData, setBodyTypeData] = useState<CodeItem | null>(null)
  const [loading, setLoading] = useState(true)

  // bodyType은 URL-encoded되어 올 수 있음
  const decodedBodyType = decodeURIComponent(bodyType)

  useEffect(() => {
    if (!decodedBodyType) return
    getBodyTypes()
      .then(types => {
        const found = types.find(t => t.code === decodedBodyType || t.name === decodedBodyType) ?? null
        setBodyTypeData(found)
      })
      .catch(() => setBodyTypeData(null))
      .finally(() => setLoading(false))
  }, [decodedBodyType])

  const displayName = bodyTypeData?.name ?? decodedBodyType
  const carCount = bodyTypeData?.carCount ?? 0
  const icon = BODY_TYPE_ICON[displayName] ?? '🚗'
  const desc = BODY_TYPE_DESC[displayName]
    ?? `${displayName} 중고차를 여러 플랫폼에서 통합 비교하세요.`

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
        { '@type': 'ListItem', position: 2, name: `${displayName} 중고차`, item: `https://www.carizon.shop/cars/type/${encodeURIComponent(displayName)}` },
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
  }, [displayName, carCount, desc])

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="animate-pulse bg-gray-100 rounded-2xl h-40 mb-8" />
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* 브레드크럼 */}
      <nav className="text-xs text-gray-400 mb-4 flex items-center gap-1.5">
        <Link to="/" className="hover:text-blue-600">홈</Link>
        <span>›</span>
        <span className="text-gray-600 font-medium">{displayName} 중고차</span>
      </nav>

      {/* ── 차종 배너 ── */}
      <div className="bg-gradient-to-br from-slate-50 to-green-50 rounded-2xl p-6 mb-8 border border-slate-100">
        <div className="flex items-center gap-4 mb-4">
          <div className="w-16 h-16 rounded-2xl bg-white shadow-sm flex items-center justify-center text-3xl">
            {icon}
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
      <SeoCarsSection fixedParams={{ bodyType: decodedBodyType }} />
    </div>
  )
}
