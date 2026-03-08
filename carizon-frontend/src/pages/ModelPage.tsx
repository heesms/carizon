import React, { useEffect, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { getModelSeo, getMakerLogoUrl, type ModelSeoData } from '@/api/seo'
import SeoCarsSection from '@/components/SeoCarsSection'
import { makerSlugToCode } from '@/utils/slugs'

const SITE_NAME = 'Carizon'

function MakerLogo({ makerCode, makerName }: { makerCode: string; makerName: string }) {
  const [failed, setFailed] = useState(false)
  if (failed) return null
  return (
    <img
      src={getMakerLogoUrl(makerCode)}
      alt={`${makerName} 로고`}
      className="w-10 h-10 object-contain rounded-xl bg-white p-0.5 shadow-sm"
      onError={() => setFailed(true)}
    />
  )
}

function ModelImage({ src, alt }: { src: string; alt: string }) {
  const [imgSrc, setImgSrc] = useState(src || '/image/no-car.svg')
  useEffect(() => { setImgSrc(src || '/image/no-car.svg') }, [src])
  return (
    <img
      src={imgSrc}
      alt={alt}
      className="w-full h-full object-cover rounded-xl"
      onError={() => { if (imgSrc !== '/image/no-car.svg') setImgSrc('/image/no-car.svg') }}
    />
  )
}

function formatPriceRange(min?: number | null, max?: number | null): string | null {
  if (min == null && max == null) return null
  // 너무 벗어난 가격 제거 (0 또는 너무 극단적인 값)
  const safeMin = (min != null && min > 0 && min < 100000) ? min : null
  const safeMax = (max != null && max > 0 && max < 100000) ? max : null
  if (safeMin != null && safeMax != null) return `${safeMin.toLocaleString()}만원 ~ ${safeMax.toLocaleString()}만원`
  if (safeMin != null) return `${safeMin.toLocaleString()}만원 ~`
  if (safeMax != null) return `~ ${safeMax.toLocaleString()}만원`
  return null
}

export default function ModelPage() {
  const { makerSlug = '', modelCode = '' } = useParams<{ makerSlug: string; modelCode: string }>()
  const makerCode = makerSlugToCode(makerSlug) ?? ''
  const navigate = useNavigate()
  const [data, setData] = useState<ModelSeoData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!modelCode) return
    getModelSeo(modelCode, makerCode || undefined)
      .then(d => setData(d))
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [modelCode, makerCode])

  const modelName = data?.modelName ?? ''
  const makerName = data?.makerName ?? ''
  const carCount = data?.carCount ?? 0
  const priceRange = formatPriceRange(data?.priceMin, data?.priceMax)
  const imageUrl = data?.imageUrl ?? `/image/model/${modelCode}.png`
  const description = data?.description ?? ''

  // SEO 메타 동적 적용
  useEffect(() => {
    if (!modelName) return
    const title = `${modelName} 중고차 | ${SITE_NAME}`
    document.title = title

    const descText = description
      ? description.split('\n')[0].trim().slice(0, 120)
      : `${modelName} 중고차 ${carCount.toLocaleString()}대${priceRange ? `, ${priceRange}` : ''}. 여러 플랫폼 통합 비교.`
    const fullDesc = priceRange
      ? `${descText} 현재 ${carCount.toLocaleString()}대, ${priceRange}.`
      : `${descText} 현재 ${carCount.toLocaleString()}대.`

    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', fullDesc)
    document.querySelector<HTMLMetaElement>('meta[property="og:title"]')?.setAttribute('content', title)
    document.querySelector<HTMLMetaElement>('meta[property="og:description"]')?.setAttribute('content', fullDesc)
    if (imageUrl) {
      document.querySelector<HTMLMetaElement>('meta[property="og:image"]')?.setAttribute('content', imageUrl)
    }

    // Breadcrumb + ItemList JSON-LD
    const jsonLd = {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: '홈', item: 'https://www.carizon.shop/' },
        { '@type': 'ListItem', position: 2, name: `${makerName} 중고차`, item: `https://www.carizon.shop/cars/maker/${makerSlug}` },
        { '@type': 'ListItem', position: 3, name: `${modelName} 중고차`, item: `https://www.carizon.shop/cars/maker/${makerSlug}/${modelCode}` },
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
  }, [modelName, makerName, modelCode, makerSlug, carCount, priceRange, description, imageUrl])

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="animate-pulse bg-gray-100 rounded-2xl h-56 mb-8" />
      </div>
    )
  }

  if (!data) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-20 text-center text-gray-400">
        모델을 찾을 수 없습니다.
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
          <Link to={`/cars/maker/${makerSlug}`} className="hover:text-blue-600">{makerName} 중고차</Link>
          <span>›</span>
          <span className="text-gray-600 font-medium">{modelName} 중고차</span>
        </nav>
      </div>

      {/* ── 모델 배너 ── */}
      <div className="bg-gradient-to-br from-slate-50 to-blue-50 rounded-2xl overflow-hidden border border-slate-100 mb-8">
        <div className="flex flex-col sm:flex-row">
          {/* 모델 이미지 */}
          <div className="sm:w-56 h-48 sm:h-auto shrink-0">
            <ModelImage src={imageUrl} alt={`${modelName} 이미지`} />
          </div>

          {/* 텍스트 영역 */}
          <div className="flex-1 p-6">
            {/* 메이커 로고 + 이름 */}
            <div className="flex items-center gap-2 mb-3">
              <MakerLogo makerCode={data.makerCode} makerName={makerName} />
              <Link
                to={`/cars/maker/${makerSlug}`}
                className="text-sm text-blue-600 hover:underline font-medium"
              >
                {makerName}
              </Link>
            </div>

            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900 mb-2">
              {modelName} 중고차
            </h1>

            {/* 통계 뱃지 */}
            <div className="flex flex-wrap gap-2 mb-4">
              <span className="bg-blue-50 text-blue-700 text-sm px-3 py-1 rounded-full font-medium">
                {carCount.toLocaleString()}대 매물
              </span>
              {priceRange && (
                <span className="bg-amber-50 text-amber-700 text-sm px-3 py-1 rounded-full font-medium">
                  {priceRange}
                </span>
              )}
            </div>

            {/* 모델 설명 (embed_text3) */}
            {description && (
              <p className="text-gray-600 text-sm leading-relaxed line-clamp-4">
                {description}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* ── 차량 목록 ── */}
      <h2 className="text-lg font-semibold text-gray-800 mb-4">
        {modelName} 중고차 매물
      </h2>
      <SeoCarsSection fixedParams={{ modelCode, ...(makerCode ? { makerCode } : {}) }} />
    </div>
  )
}
