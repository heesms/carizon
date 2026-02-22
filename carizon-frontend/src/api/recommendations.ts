const j = async (r: Response) => {
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const json = await r.json()
  return json?.data ?? json
}

export type RecommendedCar = {
  carId: number
  maker: string
  model: string
  trim?: string
  year?: number
  mileage?: number
  price?: number
  fuel?: string
  region?: string
  imageUrl?: string
  pcUrl?: string
  mUrl?: string
  relevanceScore?: number
  reason?: string
}

export type RecommendationResponse = {
  recommendation: string
  cars: RecommendedCar[]
}

export type RecommendationRequest = {
  query: string
  maxResults?: number
  minPrice?: number
  maxPrice?: number
  minYear?: number
  bodyTypeFilter?: string
  fuelFilter?: string
  intent?: string
  useLlm?: boolean
}

export const getRecommendations = (body: RecommendationRequest): Promise<RecommendationResponse> =>
  fetch('/api/recommendations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ maxResults: 5, ...body }),
  }).then(j)
