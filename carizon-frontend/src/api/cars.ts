const j = async (r: Response) => {
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const json = await r.json()
  // 백엔드 공통 포맷: { success, data, ... }
  return json?.data ?? json
}

// ─── 타입 ──────────────────────────────────────────────
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

export type CarListResponse = {
  content: CarListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type PlatformRow = {
  platformCarId: number
  platformName: string
  price?: number
  status?: string
  pcUrl?: string
  mUrl?: string
  lastSeenDate?: string
  representativeImageUrl?: string
}

export type PricePoint = { checkedAt: string; price: number }

export type CarDetailData = {
  car: {
    carId: number
    carNo?: string
    maker: string
    model: string
    modelGroup?: string
    trim?: string
    year?: number
    mileage?: number
    displacement?: number
    fuel?: string
    transmission?: string
    color?: string
    bodyType?: string
    region?: string
    priceNew?: number
    modelCode?: string
    representativeImageUrl?: string
    seatCount?: number
    myAccidentCnt?: number
  }
  platformRows: PlatformRow[]
  priceHistory?: PricePoint[]
}

// ─── API ──────────────────────────────────────────────
const qs = (params: Record<string, any>) => {
  const usp = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') usp.append(k, String(v))
  })
  return usp.toString()
}

export const searchCars = (params: Record<string, any>): Promise<CarListResponse> =>
  fetch(`/api/cars?${qs({ size: 20, page: 0, ...params })}`).then(j)

export const getCarDetail = async (id: string | number): Promise<CarDetailData> => {
  const raw = await fetch(`/api/cars/${id}`).then(j)
  // 백엔드 응답: { carId, content: CarDetailRow[], representativeImageUrl }
  // → 프론트 형태: { car: {...}, platformRows: [...] } 로 변환
  const rows: any[] = raw?.content ?? []
  const first = rows[0]
  if (!first) throw new Error('No data')
  return {
    car: {
      carId: raw.carId,
      maker: first.makerName ?? first.maker_name,
      model: first.modelName ?? first.model_name,
      modelGroup: first.modelGroupName ?? first.model_group_name,
      trim: first.trimName ?? first.trim_name,
      year: first.year,
      mileage: first.mileage,
      displacement: first.displacement,
      fuel: first.fuel,
      transmission: first.transmission,
      color: first.color,
      bodyType: first.bodyType ?? first.body_type,
      region: first.region,
      modelCode: first.modelCode ?? first.model_code,
      representativeImageUrl: raw.representativeImageUrl ?? first.representativeImageUrl,
      seatCount: first.seatCount ?? first.seat_count,
      myAccidentCnt: first.myAccidentCnt ?? first.my_accident_cnt,
    },
    platformRows: rows.map(r => ({
      platformCarId: r.platformCarId,
      platformName: r.platformName ?? r.platform_name,
      price: r.price,
      status: r.status,
      pcUrl: r.pcUrl ?? r.pc_url,
      mUrl: r.mUrl ?? r.m_url,
      lastSeenDate: r.lastSeenDate,
      representativeImageUrl: r.representativeImageUrl,
    })),
  }
}

export const getPriceHistory = (id: string | number): Promise<{ points: PricePoint[] }> =>
  fetch(`/api/cars/${id}/price-history`).then(j)
