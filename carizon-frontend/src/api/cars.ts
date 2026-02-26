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
  optionArray?: string
  representativeImageUrl?: string
  extra?: string
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

const toFiniteNumber = (value: unknown): number | undefined => {
  if (value == null) return undefined
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined
  const raw = String(value).replace(/,/g, '').trim()
  if (!raw) return undefined
  const n = Number(raw)
  return Number.isFinite(n) ? n : undefined
}

const parseExtraJson = (value: unknown): any | undefined => {
  if (value == null) return undefined
  if (typeof value === 'object') return value
  if (typeof value !== 'string') return undefined
  const trimmed = value.trim()
  if (!trimmed) return undefined
  try {
    return JSON.parse(trimmed)
  } catch {
    return undefined
  }
}

const findNumericByKeys = (source: unknown, keys: string[]): number | undefined => {
  if (source == null || typeof source !== 'object') return undefined
  const queue: any[] = [source]
  while (queue.length > 0) {
    const current = queue.shift()
    if (current == null || typeof current !== 'object') continue
    for (const key of keys) {
      const direct = toFiniteNumber(current[key])
      if (direct != null) return direct
    }
    Object.values(current).forEach((value) => {
      if (value && typeof value === 'object') queue.push(value)
    })
  }
  return undefined
}

const inferNumericFromRows = (rows: any[], keys: string[]): number | undefined => {
  for (const row of rows) {
    const direct = findNumericByKeys(row, keys)
    if (direct != null) return direct
    const fromExtra = findNumericByKeys(parseExtraJson(row?.extra), keys)
    if (fromExtra != null) return fromExtra
  }
  return undefined
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
  const inferredSeatCount = inferNumericFromRows(rows, ['seatCount', 'seat_count'])
  const inferredMyAccidentCnt = inferNumericFromRows(rows, ['myAccidentCnt', 'my_accident_cnt', 'myAccidentCount', 'accidentCnt', 'accident_count'])
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
      seatCount: first.seatCount ?? first.seat_count ?? inferredSeatCount,
      myAccidentCnt: first.myAccidentCnt ?? first.my_accident_cnt ?? inferredMyAccidentCnt,
    },
    platformRows: rows.map(r => ({
      platformCarId: r.platformCarId,
      platformName: r.platformName ?? r.platform_name,
      price: r.price,
      status: r.status,
      pcUrl: r.pcUrl ?? r.pc_url,
      mUrl: r.mUrl ?? r.m_url,
      lastSeenDate: r.lastSeenDate,
      optionArray: r.optionArray ?? r.option_array,
      representativeImageUrl: r.representativeImageUrl,
      extra: r.extra,
    })),
  }
}

export const getPriceHistory = (id: string | number): Promise<{ points: PricePoint[] }> =>
  fetch(`/api/cars/${id}/price-history`).then(j)
