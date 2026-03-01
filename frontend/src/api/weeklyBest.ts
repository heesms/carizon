export type WeeklyBestCar = {
  carId: number
  platformCarId?: number
  platformName?: string
  makerCode?: string
  makerName?: string
  modelGroupCode?: string
  modelGroupName?: string
  modelCode?: string
  modelName?: string
  trimCode?: string
  trimName?: string
  gradeCode?: string
  gradeName?: string
  year?: number
  mileage?: number
  price?: number
  fuel?: string
  transmission?: string
  bodyType?: string
  region?: string
  status?: string
  pcUrl?: string
  mUrl?: string
  lastSeenDate?: string
  carImageUrl?: string
}

type ApiResponse<T> = {
  success: boolean
  data: T
}

export async function getHomeWeeklyBest(limit = 20): Promise<WeeklyBestCar[]> {
  const res = await fetch(`/api/recommendation/weekly-best/home?limit=${encodeURIComponent(String(limit))}`)
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Unknown error' }))
    throw new Error(error?.message || `HTTP ${res.status}`)
  }
  const apiResponse: ApiResponse<WeeklyBestCar[]> = await res.json()
  return apiResponse.data
}
