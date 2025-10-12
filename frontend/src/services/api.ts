const API_BASE = import.meta.env.VITE_API_BASE as string

export type CodeItem = { code: string, name: string }

export async function getMakers(): Promise<CodeItem[]> {
  const r = await fetch(`${API_BASE}/api/codes/makers`)
  return r.json()
}
export async function getModelGroups(makerCode: string): Promise<CodeItem[]> {
  const r = await fetch(`${API_BASE}/api/codes/model-groups?makerCode=${encodeURIComponent(makerCode)}`)
  return r.json()
}
export async function getModels(makerCode: string, modelGroupCode: string): Promise<CodeItem[]> {
  const u = `${API_BASE}/api/codes/models?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}`
  const r = await fetch(u); return r.json()
}
export async function getTrims(makerCode: string, modelGroupCode: string, modelCode: string): Promise<CodeItem[]> {
  const u = `${API_BASE}/api/codes/trims?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}`
  const r = await fetch(u); return r.json()
}
export async function getGrades(makerCode: string, modelGroupCode: string, modelCode: string, trimCode: string): Promise<CodeItem[]> {
  const u = `${API_BASE}/api/codes/grades?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}&trimCode=${encodeURIComponent(trimCode)}`
  const r = await fetch(u); return r.json()
}

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
}

export type CarListResponse = {
  content: CarListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export async function searchCars(params: Record<string, any>): Promise<CarListResponse> {
  const usp = new URLSearchParams()
  Object.entries(params).forEach(([k,v])=>{
    if (v !== undefined && v !== null && v !== '') usp.append(k, String(v))
  })
  const r = await fetch(`${API_BASE}/api/cars?` + usp.toString())
  return r.json()
}

export type CarDetail = {
  carId: number
  specs: Record<string, any>
  platforms: { platformCarId: number, platform: string, price: number, status: string, pcUrl?: string, mUrl?: string, lastSeenDate?: string }[]
  recommended: number[]
}

export async function getCarDetail(id: string | number): Promise<CarDetail> {
  const r = await fetch(`${API_BASE}/api/cars/${id}`)
  return r.json()
}

export type PricePoint = { ts: string, price: number }
export async function getPriceHistory(carId: string | number, platformCarId?: number): Promise<{points: PricePoint[]}> {
  const u = new URL(`${API_BASE}/api/cars/${carId}/price-history`)
  if (platformCarId) u.searchParams.set('platformCarId', String(platformCarId))
  const r = await fetch(u)
  return r.json()
}

export async function getModelImages(modelCode: string): Promise<{imageUrl: string, isMain: boolean, sortOrder: number}[]>{
  const r = await fetch(`${API_BASE}/api/models/${encodeURIComponent(modelCode)}/images`)
  return r.json()
}
