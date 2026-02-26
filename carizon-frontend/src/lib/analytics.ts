/* eslint-disable @typescript-eslint/no-explicit-any */
declare function gtag(...args: any[]): void

function track(eventName: string, params?: Record<string, any>) {
  if (typeof gtag === 'undefined') return
  gtag('event', eventName, params)
}

// ── 검색 ─────────────────────────────────────────
export function trackSearch(query: string) {
  track('search', { search_term: query })
}

export function trackFilterApply(filters: Record<string, any>) {
  track('filter_apply', {
    maker_code: filters.makerCode,
    model_code: filters.modelCode,
    fuel: filters.fuel,
    body_type: filters.bodyType,
    price_min: filters.priceMin,
    price_max: filters.priceMax,
    year_min: filters.yearMin,
    year_max: filters.yearMax,
    km_max: filters.kmMax,
  })
}

export function trackQuickSearch(label: string) {
  track('quick_search_click', { label })
}

// ── 매물 ─────────────────────────────────────────
export function trackSelectItem(carId: number, maker: string, model: string, source: string) {
  track('select_item', {
    items: [{ item_id: String(carId), item_name: `${maker} ${model}` }],
    content_type: 'car',
    source,
  })
}

export function trackViewItem(carId: number, maker: string, model: string, price?: number) {
  track('view_item', {
    currency: 'KRW',
    value: price ? price * 10000 : undefined,
    items: [{ item_id: String(carId), item_name: `${maker} ${model}`, price }],
  })
}

export function trackPlatformLinkClick(platform: string, carId: number) {
  track('platform_link_click', { platform, car_id: carId })
}

// ── 좋아요 ───────────────────────────────────────
export function trackLike(carId: number, liked: boolean) {
  track(liked ? 'car_like' : 'car_unlike', { car_id: carId })
}

// ── AI 추천 ──────────────────────────────────────
export function trackAiRecommendation(query: string, source: 'input' | 'quick_prompt' | 'url') {
  track('ai_recommendation_request', { query, source })
}

export function trackAiPromptClick(prompt: string) {
  track('ai_prompt_click', { prompt })
}
