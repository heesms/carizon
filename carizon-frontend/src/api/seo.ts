const j = async (r: Response) => {
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const json = await r.json()
  return json?.data ?? json
}

export type ModelSeoData = {
  modelCode: string
  modelName: string
  makerCode: string
  makerName: string
  description?: string | null
  imageUrl?: string | null
  carCount: number
  priceMin?: number | null
  priceMax?: number | null
}

const modelSeoCache = new Map<string, Promise<ModelSeoData>>()

export const getModelSeo = (modelCode: string, makerCode?: string): Promise<ModelSeoData> => {
  const key = `${modelCode.trim()}:${makerCode ?? ''}`
  if (modelSeoCache.has(key)) return modelSeoCache.get(key)!
  const qs = makerCode ? `?makerCode=${encodeURIComponent(makerCode)}` : ''
  const req = fetch(`/api/seo/model/${encodeURIComponent(modelCode.trim())}${qs}`)
    .then(j)
    .finally(() => modelSeoCache.delete(key))
  modelSeoCache.set(key, req)
  return req
}

export const getMakerLogoUrl = (makerCode: string): string =>
  `https://img.kbchachacha.com/IMG/statics/maker/o/maker${makerCode}.png`

export const getModelsByMaker = (makerCode: string, limit = 30): Promise<ModelSeoData[]> =>
  fetch(`/api/seo/models?makerCode=${encodeURIComponent(makerCode)}&limit=${limit}`)
    .then(j)
