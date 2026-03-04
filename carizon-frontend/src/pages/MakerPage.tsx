import React, { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getMakers, type CodeItem } from '@/api/codes'
import { getMakerLogoUrl } from '@/api/seo'
import SeoCarsSection from '@/components/SeoCarsSection'
import { makerSlugToCode, makerSlugToDisplayName } from '@/utils/slugs'

const SITE_NAME = 'Carizon'

function MakerLogo({ makerCode, makerName }: { makerCode: string; makerName: string }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    return (
      <div className="w-16 h-16 rounded-2xl bg-gray-100 flex items-center justify-center text-gray-400 text-xs font-bold">
        {makerName.slice(0, 2)}
      </div>
    )
  }
  return (
    <img
      src={getMakerLogoUrl(makerCode)}
      alt={`${makerName} 로고`}
      className="w-16 h-16 object-contain rounded-2xl bg-white p-1 shadow-sm"
      onError={() => setFailed(true)}
    />
  )
}


export default function MakerPage() {
  const { makerSlug = '' } = useParams<{ makerSlug: string }>()
  const makerCode = makerSlugToCode(makerSlug) ?? ''
  const [maker, setMaker] = useState<CodeItem | null>(null)
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    if (!makerCode) { setLoading(false); return }
    getMakers()
      .then(makers => {
        const found = makers.find(m => m.code === makerCode) ?? null
        setMaker(found)
      })
      .catch(() => setMaker(null))
      .finally(() => setLoading(false))
  }, [makerCode])

  const makerName = maker?.name ?? makerSlugToDisplayName(makerSlug) ?? ''
  const carCount = maker?.carCount ?? 0
  const isDomestic = maker?.domestic === 1 || maker?.domestic === true || maker?.domestic === '1'

  // SEO 메타 동적 적용
  useEffect(() => {
    if (!makerName) return
    const title = `${makerName} 중고차 | ${SITE_NAME}`
    document.title = title
    const desc = `${makerName} 중고차 ${carCount.toLocaleString()}대를 여러 플랫폼에서 통합 비교하세요. 가격, 연식, 주행거리를 한눈에 확인.`
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', desc)
    document.querySelector<HTMLMetaElement>('meta[property="og:title"]')?.setAttribute('content', title)
    document.querySelector<HTMLMetaElement>('meta[property="og:description"]')?.setAttribute('content', desc)
    // Breadcrumb JSON-LD
    const jsonLd = {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: '홈', item: 'https://www.carizon.shop/' },
        { '@type': 'ListItem', position: 2, name: `${makerName} 중고차`, item: `https://www.carizon.shop/cars/maker/${makerSlug}` },
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
  }, [makerName, makerSlug, carCount])

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="animate-pulse flex gap-4 items-center mb-8">
          <div className="w-16 h-16 bg-gray-200 rounded-2xl" />
          <div>
            <div className="h-7 bg-gray-200 rounded w-40 mb-2" />
            <div className="h-4 bg-gray-200 rounded w-28" />
          </div>
        </div>
      </div>
    )
  }

  if (!makerCode) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-20 text-center text-gray-400">
        브랜드를 찾을 수 없습니다.
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* 브레드크럼 */}
      <nav className="text-xs text-gray-400 mb-4 flex items-center gap-1.5">
        <Link to="/" className="hover:text-blue-600">홈</Link>
        <span>›</span>
        <span className="text-gray-600 font-medium">{makerName} 중고차</span>
      </nav>

      {/* ── 브랜드 배너 ── */}
      <div className="bg-gradient-to-br from-slate-50 to-blue-50 rounded-2xl p-6 mb-8 border border-slate-100">
        <div className="flex items-center gap-4 mb-4">
          <MakerLogo makerCode={makerCode} makerName={makerName} />
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                isDomestic
                  ? 'bg-blue-100 text-blue-700'
                  : 'bg-purple-100 text-purple-700'
              }`}>
                {isDomestic ? '국산' : '수입'}
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">
              {makerName} 중고차
            </h1>
          </div>
        </div>

        <p className="text-gray-600 text-sm leading-relaxed mb-3">
          {makerName} 중고차를 여러 플랫폼에서 통합 비교하세요.
          현재 <strong>{carCount.toLocaleString()}대</strong>의 {makerName} 매물이 등록되어 있습니다.
        </p>

        <div className="flex items-center gap-3 text-sm text-gray-500">
          <span className="flex items-center gap-1">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            {carCount.toLocaleString()}대 매물
          </span>
          <span>·</span>
          <span>여러 플랫폼 통합 비교</span>
        </div>
      </div>

      {/* ── 차량 목록 ── */}
      <h2 className="text-lg font-semibold text-gray-800 mb-4">
        {makerName} 중고차 매물
      </h2>
      <SeoCarsSection fixedParams={{ makerCode }} />
    </div>
  )
}
