// ─────────────────────────────────────────────
//  Carizon API client  (백엔드 /api/* 완전 호환)
// ─────────────────────────────────────────────
const BASE = ''

async function j<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = await res.json()
  // 백엔드 표준 래퍼 { success, data, ... } 처리
  return (body?.data !== undefined ? body.data : body) as T
}

function qs(params: Record<string, unknown>): string {
  const usp = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') usp.append(k, String(v))
  })
  return usp.toString()
}

// ── 코드 조회 ──────────────────────────────────
export type CodeItem = { code: string; name: string }

export const getMakers       = ()                                                  => fetch(`${BASE}/api/codes/makers`).then(r => j<CodeItem[]>(r))
export const getModelGroups  = (makerCode: string)                                => fetch(`${BASE}/api/codes/model-groups?makerCode=${encodeURIComponent(makerCode)}`).then(r => j<CodeItem[]>(r))
export const getModels       = (makerCode: string, modelGroupCode: string)        => fetch(`${BASE}/api/codes/models?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}`).then(r => j<CodeItem[]>(r))
export const getTrims        = (makerCode: string, mgc: string, mc: string)       => fetch(`${BASE}/api/codes/trims?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(mgc)}&modelCode=${encodeURIComponent(mc)}`).then(r => j<CodeItem[]>(r))
export const getGrades       = (makerCode: string, mgc: string, mc: string, tc: string) => fetch(`${BASE}/api/codes/grades?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(mgc)}&modelCode=${encodeURIComponent(mc)}&trimCode=${encodeURIComponent(tc)}`).then(r => j<CodeItem[]>(r))

// ── 차량 목록 ──────────────────────────────────
export type CarListItem = {
  carId: number
  maker: string
  model: string
  trim?: string
  year?: number
  km?: number
  priceMin?: number
  priceMax?: number
  priceUpdatedAt?: string
  representativeImageUrl?: string
  modelCode?: string
  fuel?: string
  region?: string
}

export type CarPage = {
  content: CarListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type SearchParams = {
  q?: string
  maker?: string; makerCode?: string
  modelGroup?: string; modelGroupCode?: string
  model?: string; modelCode?: string
  trim?: string; trimCode?: string
  gradeCode?: string
  carNo?: string
  priceMin?: number; priceMax?: number
  yearMin?: number;  yearMax?: number
  kmMax?: number
  fuel?: string; region?: string
  sort?: 'RECENT' | 'LOW_PRICE' | 'LOW_KM' | 'NEW_YEAR' | ''
  page?: number
  size?: number
}

export const searchCars = (params: SearchParams): Promise<CarPage> =>
  fetch(`${BASE}/api/cars?${qs({ size: 20, page: 0, ...params } as Record<string, unknown>)}`).then(r => j<CarPage>(r))

// ── 차량 상세 ──────────────────────────────────
export type PlatformRow = {
  platformCarId: number
  platform: string
  price?: number
  status?: string
  pcUrl?: string
  mUrl?: string
  lastSeenDate?: string
}

export type CarSpecs = {
  maker?: string; model?: string; modelGroup?: string; trim?: string
  year?: number; km?: number; fuel?: string; transmission?: string
  color?: string; bodyType?: string; region?: string; displacement?: number
  modelCode?: string
}

export type CarDetail = {
  carId: number
  numberPlate?: string
  specs: CarSpecs
  platforms: PlatformRow[]
  recommended?: number[]
}

export type PricePoint = { ts: string; price: number }

export const getCarDetail     = (id: string | number): Promise<CarDetail> =>
  fetch(`${BASE}/api/cars/${id}`).then(async r => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    const body = await r.json()
    const raw = body?.data || body
    // CarDetailRow[] → CarDetail 변환 (기존 CarDetail.tsx 로직 재사용)
    if (raw?.content && Array.isArray(raw.content) && raw.content.length > 0) {
      const first = raw.content[0]
      const specs: CarSpecs = {
        maker: first.makerName, model: first.modelName, modelGroup: first.modelGroupName,
        trim: first.trimName, year: first.year, km: first.mileage,
        fuel: first.fuel, transmission: first.transmission, color: first.color,
        bodyType: first.bodyType, region: first.region, displacement: first.displacement,
        modelCode: first.modelCode,
      }
      const platformMap = new Map<number, PlatformRow>()
      raw.content.forEach((row: Record<string, unknown>) => {
        if (row.platformCarId) platformMap.set(row.platformCarId as number, {
          platformCarId: row.platformCarId as number,
          platform: (row.platformName as string) || '',
          price: row.price as number | undefined,
          status: row.status as string | undefined,
          pcUrl: row.pcUrl as string | undefined,
          mUrl: row.mUrl as string | undefined,
          lastSeenDate: row.lastSeenDate as string | undefined,
        })
      })
      return { carId: raw.carId || Number(id), numberPlate: raw.numberPlate, specs, platforms: Array.from(platformMap.values()), recommended: [] }
    }
    return raw as CarDetail
  })

export const getPriceHistory  = (id: string | number, platformCarId?: number): Promise<{ points: PricePoint[] }> => {
  const u = new URL(`${BASE}/api/cars/${id}/price-history`, window.location.origin)
  if (platformCarId) u.searchParams.set('platformCarId', String(platformCarId))
  return fetch(u.toString()).then(r => j(r))
}

// ── RAG 추천 ───────────────────────────────────
export type RecommendedCar = {
  carId: number
  maker?: string; model?: string; trim?: string
  year?: number; mileage?: number; price?: number
  fuel?: string; transmission?: string; color?: string; region?: string
  reason?: string; relevanceScore?: number
  imageUrl?: string; car_image_url?: string
  pcUrl?: string; mUrl?: string; url?: string
  modelCode?: string
}

export type RecommendationRequest = {
  query: string
  maxResults?: number
  minPrice?: number; maxPrice?: number
  maker?: string; fuel?: string; bodyType?: string
  yearMin?: number; yearMax?: number
  intent?: string
  useLlm?: boolean
}

export type RecommendationResponse = {
  recommendation?: string
  cars?: RecommendedCar[]
}

export const getRecommendations = (req: RecommendationRequest): Promise<RecommendationResponse> =>
  fetch(`${BASE}/api/v2/recommendations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  }).then(r => j(r))

// ── AI 랭킹 BEST ────────────────────────────────
export type AiRankingResult = {
  title: string
  content: string
  carCount?: number
}

export const getAiRankingBest = (modelCode: string): Promise<AiRankingResult> =>
  fetch(`${BASE}/admin/recommendation/weekly-best/model/${encodeURIComponent(modelCode)}/generate?limit=10`, {
    method: 'POST',
  }).then(r => j(r))

// ── 좋아요 ─────────────────────────────────────
export type LikeInfo = { count: number; liked: boolean }

export const getLikeInfo  = (carId: number): Promise<LikeInfo>  => fetch(`${BASE}/api/likes/${carId}`).then(r => j(r))
export const toggleLike   = (carId: number): Promise<LikeInfo>  =>
  fetch(`${BASE}/api/likes/${carId}`, { method: 'POST' }).then(r => j(r))

// ── 플랫폼명 한글 변환 ─────────────────────────
export function platformName(p: string): string {
  const m: Record<string, string> = {
    chachacha: '차차차', encar: '엔카', chutcha: '첫차',
    kcar: 'K카', tcar: 'TCAR', charancha: '차란차',
  }
  return m[p?.toLowerCase()] ?? p
}

export function platformClass(p: string): string {
  const m: Record<string, string> = {
    encar: 'platform-encar', kcar: 'platform-kcar',
    chachacha: 'platform-chachacha', chutcha: 'platform-chutcha',
    tcar: 'platform-tcar', charancha: 'platform-charancha',
  }
  return m[p?.toLowerCase()] ?? ''
}
