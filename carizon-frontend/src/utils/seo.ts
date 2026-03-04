const SITE_NAME = 'Carizon'
const DEFAULT_KEYWORDS = '중고차, 중고차 검색, 중고차 매물, 차량 추천, Carizon'
const DEFAULT_OG_IMAGE = '/og-image.png'

const SITE_URL = String(import.meta.env.VITE_SITE_URL || '').replace(/\/$/, '')
const OG_IMAGE = String(import.meta.env.VITE_SITE_OG_IMAGE || '').trim() || DEFAULT_OG_IMAGE

export type SeoMetadata = {
  title: string
  description: string
  keywords: string
  noIndex?: boolean
  canonicalPath?: string
}

const getSearchSummary = (search: string) => {
  const params = new URLSearchParams(search)
  const makerCode = params.get('makerCode')
  const modelCode = params.get('modelCode')
  const bodyType = params.get('bodyType')
  const keyword = params.get('q') || params.get('query')
  const keywordParts = [
    keyword,
    makerCode ? `제조사코드:${makerCode}` : '',
    modelCode ? `모델코드:${modelCode}` : '',
    bodyType ? `차종:${bodyType}` : '',
  ].filter(Boolean)

  return keywordParts.length > 0 ? `(${keywordParts.join(', ')})` : ''
}

const getRouteMetadata = (pathname: string, search = ''): SeoMetadata => {
  const trimmed = pathname.replace(/\/+$/, '') || '/'
  const searchSummary = getSearchSummary(search)

  if (trimmed === '/' || trimmed === '') {
    return {
      title: '홈',
      description: '차량을 한눈에 비교하고 AI 추천으로 빠르게 찾는 중고차 통합 서비스',
      keywords: `차량검색, 중고차 검색, AI 추천, ${DEFAULT_KEYWORDS}`,
    }
  }

  if (trimmed.startsWith('/search')) {
    const params = new URLSearchParams(search)
    const makerCode = (params.get('makerCode') || '').trim()
    const modelCode = (params.get('modelCode') || '').trim()
    const bodyType = (params.get('bodyType') || '').trim()
    const pageRaw = (params.get('page') || '').trim()
    const sortRaw = (params.get('sort') || '').trim()
    const page = Number(pageRaw || '0')
    const hasPaging = Number.isFinite(page) && page > 0
    const hasSort = sortRaw.length > 0

    const representativePairs = [
      makerCode ? ['makerCode', makerCode] as const : null,
      modelCode ? ['modelCode', modelCode] as const : null,
      bodyType ? ['bodyType', bodyType] as const : null,
    ].filter(Boolean) as ReadonlyArray<readonly [string, string]>

    let hasOtherFilters = false
    params.forEach((value, key) => {
      const v = String(value || '').trim()
      if (!v) return
      if (key === 'makerCode' || key === 'modelCode' || key === 'bodyType' || key === 'page' || key === 'sort') return
      hasOtherFilters = true
    })

    const canonicalParams = new URLSearchParams()
    if (representativePairs.length > 0) {
      const preferred = representativePairs[0]
      canonicalParams.set(preferred[0], preferred[1])
    }
    const canonicalQuery = canonicalParams.toString()
    const canonicalPath = canonicalQuery ? `/search?${canonicalQuery}` : '/search'

    const indexableRepresentative = representativePairs.length === 1 && !hasOtherFilters && !hasPaging && !hasSort
    const noIndex = !indexableRepresentative && params.toString().length > 0

    const suffix = searchSummary ? ` ${searchSummary}` : ''
    return {
      title: '차량 검색',
      description: `중고차 검색 조건${suffix}에 맞는 매물을 조회합니다.`,
      keywords: `중고차 검색${suffix}, 조건 검색, ${DEFAULT_KEYWORDS}`,
      canonicalPath,
      noIndex,
    }
  }

  if (trimmed === '/recommendation') {
    return {
      title: 'AI 추천',
      description: '희망 조건을 기반으로 AI가 중고차 후보를 추천합니다.',
      keywords: `AI 추천, 중고차 추천, Carizon AI, ${DEFAULT_KEYWORDS}`,
    }
  }

  if (trimmed === '/ai-ranking') {
    return {
      title: 'AI 매물 랭킹',
      description: 'AI 점수 기반 중고차 추천 랭킹으로 가격/품질 정보를 한 번에 비교합니다.',
      keywords: `AI 랭킹, 중고차 랭킹, 가격 비교, ${DEFAULT_KEYWORDS}`,
    }
  }

  if (trimmed === '/likes') {
    return {
      title: '찜한 차량',
      description: '관심 차량을 저장해두고 나중에 다시 비교할 수 있습니다.',
      keywords: `찜한 차량, 관심 차량, 중고차 추적, ${DEFAULT_KEYWORDS}`,
    }
  }

  if (trimmed.startsWith('/cars/')) {
    const carId = trimmed.split('/').pop() || 'id'
    return {
      title: '차량 상세',
      description: `차량 상세 정보를 확인하고 상세 스펙과 가격 변동을 확인할 수 있습니다. (${carId})`,
      keywords: `차량 상세, 매물 상세, ${DEFAULT_KEYWORDS}`,
      canonicalPath: trimmed,
    }
  }

  if (trimmed.startsWith('/info')) {
    return {
      title: '안내',
      description: 'Carizon 이용 방법, 약관, 개인정보처리방침 등 안내 페이지입니다.',
      keywords: `Carizon 안내, 이용약관, 개인정보처리방침, ${DEFAULT_KEYWORDS}`,
    }
  }

  return {
    title: '페이지',
    description: '요청하신 페이지를 찾을 수 없거나 준비 중입니다.',
    keywords: `Carizon, ${DEFAULT_KEYWORDS}`,
    noIndex: true,
  }
}

const setMetaByName = (name: string, content: string) => {
  const tag = document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
    || (() => {
      const created = document.createElement('meta')
      created.setAttribute('name', name)
      document.head.appendChild(created)
      return created
    })()
  tag.content = content
}

const setMetaProperty = (property: string, content: string) => {
  const tag = document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`)
    || (() => {
      const created = document.createElement('meta')
      created.setAttribute('property', property)
      document.head.appendChild(created)
      return created
    })()
  tag.content = content
}

const setCanonical = (url: string) => {
  const canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]')
    || (() => {
      const created = document.createElement('link')
      created.rel = 'canonical'
      document.head.appendChild(created)
      return created
    })()
  canonical.href = url
}

const getCanonicalUrl = (pathname: string, search = '') => {
  const origin = SITE_URL || (typeof window !== 'undefined' ? window.location.origin : '')
  if (!origin) return `${pathname}${search}`
  return `${origin}${pathname}${search}`
}

// ─── JSON-LD helpers ────────────────────────────────────────────────────────

const setJsonLd = (data: object) => {
  let script = document.querySelector<HTMLScriptElement>('script[type="application/ld+json"][data-car]')
  if (!script) {
    script = document.createElement('script')
    script.type = 'application/ld+json'
    script.setAttribute('data-car', '1')
    document.head.appendChild(script)
  }
  script.textContent = JSON.stringify(data)
}

const removeJsonLd = () => {
  document.querySelector('script[type="application/ld+json"][data-car]')?.remove()
}

// ─── Car detail dynamic SEO ─────────────────────────────────────────────────

export type CarSeoData = {
  id: number | string
  makerCode?: string | null
  modelCode?: string | null
  maker?: string | null
  model?: string | null
  trim?: string | null
  year?: number | null
  mileage?: number | null
  fuel?: string | null
  price?: number | null
  imageUrl?: string | null
}

export const applyCarDetailSeo = (car: CarSeoData) => {
  const name = [car.maker, car.model].filter(Boolean).join(' ')
  const titleLabel = [name, car.year ? `${car.year}년식` : ''].filter(Boolean).join(' ')
  const title = `${titleLabel || '차량 상세'} | ${SITE_NAME}`
  const canonicalOrigin = SITE_URL || (typeof window !== 'undefined' ? window.location.origin : '')
  const canonical = `${canonicalOrigin}/cars/${car.id}`

  const descParts: string[] = []
  if (car.price) descParts.push(`${car.price.toLocaleString()}만원`)
  if (car.year) descParts.push(`${car.year}년식`)
  if (car.mileage) descParts.push(`${car.mileage.toLocaleString()}km`)
  if (car.fuel) descParts.push(car.fuel)
  const description = descParts.length > 0
    ? `${name} ${descParts.join(' · ')} — 여러 중고차 플랫폼 매물을 한눈에 비교하세요.`
    : `${name} 차량 상세 정보 — 플랫폼별 가격 비교, 스펙, 가격 변동을 확인하세요.`

  const keywords = `${name}, 중고차${car.year ? `, ${car.year}년식 ${name}` : ''}, ${DEFAULT_KEYWORDS}`
  const imageUrl = car.imageUrl || OG_IMAGE

  document.title = title
  setMetaByName('description', description)
  setMetaByName('keywords', keywords)
  setMetaByName('robots', 'index,follow')
  setMetaByName('theme-color', '#0b101f')
  setMetaProperty('og:type', 'product')
  setMetaProperty('og:site_name', SITE_NAME)
  setMetaProperty('og:title', title)
  setMetaProperty('og:description', description)
  setMetaProperty('og:image', imageUrl)
  setMetaProperty('og:url', canonical)
  setMetaProperty('twitter:card', 'summary_large_image')
  setMetaProperty('twitter:title', title)
  setMetaProperty('twitter:description', description)
  setMetaProperty('twitter:image', imageUrl)
  setCanonical(canonical)

  // JSON-LD: Vehicle + BreadcrumbList
  const origin = SITE_URL || (typeof window !== 'undefined' ? window.location.origin : '')
  const breadcrumbItems: Record<string, unknown>[] = [
    { '@type': 'ListItem', position: 1, name: '홈', item: `${origin}/` },
  ]
  if (car.makerCode && car.maker) {
    breadcrumbItems.push({ '@type': 'ListItem', position: 2, name: `${car.maker} 중고차`, item: `${origin}/cars/maker/${car.makerCode}` })
    if (car.modelCode && car.model) {
      breadcrumbItems.push({ '@type': 'ListItem', position: 3, name: `${car.model} 중고차`, item: `${origin}/cars/maker/${car.makerCode}/${car.modelCode}` })
      breadcrumbItems.push({ '@type': 'ListItem', position: 4, name: name || '차량 상세', item: canonical })
    } else {
      breadcrumbItems.push({ '@type': 'ListItem', position: 3, name: name || '차량 상세', item: canonical })
    }
  } else {
    breadcrumbItems.push({ '@type': 'ListItem', position: 2, name: name || '차량 상세', item: canonical })
  }

  const vehicleJsonLd: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'Vehicle',
    name: name || '중고차',
    url: canonical,
    ...(imageUrl !== DEFAULT_OG_IMAGE ? { image: imageUrl } : {}),
  }
  if (car.maker) vehicleJsonLd.manufacturer = { '@type': 'Organization', name: car.maker }
  if (car.model) vehicleJsonLd.model = car.model
  if (car.year) vehicleJsonLd.modelDate = String(car.year)
  if (car.mileage) vehicleJsonLd.mileageFromOdometer = { '@type': 'QuantitativeValue', value: car.mileage, unitCode: 'KMT' }
  if (car.fuel) vehicleJsonLd.fuelType = car.fuel
  if (car.price) {
    vehicleJsonLd.offers = {
      '@type': 'Offer',
      priceCurrency: 'KRW',
      price: car.price * 10000,
      availability: 'https://schema.org/InStock',
      url: canonical,
    }
  }

  const jsonLd = {
    '@context': 'https://schema.org',
    '@graph': [
      vehicleJsonLd,
      { '@type': 'BreadcrumbList', itemListElement: breadcrumbItems },
    ],
  }
  setJsonLd(jsonLd)
}

export const clearCarDetailSeo = () => {
  removeJsonLd()
}

// ─── Route-based SEO ─────────────────────────────────────────────────────────

export const applyRouteSeo = (pathname: string, search = ''): SeoMetadata => {
  const meta = getRouteMetadata(pathname, search)

  const title = `${meta.title} | ${SITE_NAME}`
  document.title = title
  const description = meta.description
  const canonicalOrigin = SITE_URL || (typeof window !== 'undefined' ? window.location.origin : '')
  const canonical = meta.canonicalPath ? `${canonicalOrigin}${meta.canonicalPath}` : getCanonicalUrl(pathname, search)

  setMetaByName('description', description)
  setMetaByName('keywords', meta.keywords)
  setMetaByName('robots', meta.noIndex ? 'noindex,follow' : 'index,follow')
  setMetaByName('theme-color', '#0b101f')
  setMetaProperty('og:type', 'website')
  setMetaProperty('og:site_name', SITE_NAME)
  setMetaProperty('og:title', title)
  setMetaProperty('og:description', description)
  setMetaProperty('og:image', OG_IMAGE)
  setMetaProperty('og:url', canonical)
  setMetaProperty('twitter:card', 'summary_large_image')
  setMetaProperty('twitter:title', title)
  setMetaProperty('twitter:description', description)
  setMetaProperty('twitter:image', OG_IMAGE)
  setCanonical(canonical)

  return meta
}
