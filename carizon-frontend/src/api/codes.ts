const j = async (r: Response) => {
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const json = await r.json()
  return json?.data ?? json
}

export type CodeItem = {
  code: string
  name: string
  countryCode?: string
  domestic?: number | boolean
  carCount?: number
}

export type CodeQueryContext = {
  q?: string
  makerCode?: string
  modelGroupCode?: string
  modelCode?: string
  trimCode?: string
  yearMin?: string | number
  yearMax?: string | number
  kmMin?: string | number
  kmMax?: string | number
  priceMin?: string | number
  priceMax?: string | number
  fuel?: string
  bodyType?: string
  region?: string
  transmission?: string
  carNo?: string
}

const withContext = (path: string, context?: CodeQueryContext) => {
  const usp = new URLSearchParams()
  if (context) {
    Object.entries(context).forEach(([k, v]) => {
      if (v === undefined || v === null) return
      const value = String(v).trim()
      if (!value) return
      usp.set(k, value)
    })
  }
  const qs = usp.toString()
  if (!qs) return path
  return `${path}${path.includes('?') ? '&' : '?'}${qs}`
}

const hasContext = (context?: CodeQueryContext) =>
  !!context && Object.values(context).some(v => v !== undefined && v !== null && String(v).trim() !== '')

let makersCache: CodeItem[] | null = null
let makersPromise: Promise<CodeItem[]> | null = null
const modelGroupsCache = new Map<string, CodeItem[]>()
const modelGroupsPromise = new Map<string, Promise<CodeItem[]>>()
const modelsCache = new Map<string, CodeItem[]>()
const modelsPromise = new Map<string, Promise<CodeItem[]>>()
let bodyTypesCache: CodeItem[] | null = null
let bodyTypesPromise: Promise<CodeItem[]> | null = null

export const getMakers = (context?: CodeQueryContext): Promise<CodeItem[]> => {
  const dynamic = hasContext(context)
  if (!dynamic && makersCache) return Promise.resolve(makersCache)
  if (!dynamic && makersPromise) return makersPromise

  const req = fetch(withContext('/api/codes/makers', context))
    .then(j)
    .then((data: CodeItem[]) => {
      if (!dynamic) makersCache = data
      return data
    })
    .finally(() => {
      if (!dynamic) makersPromise = null
    })

  if (!dynamic) makersPromise = req
  return req
}

export const getModelGroups = (makerCode: string, context?: CodeQueryContext): Promise<CodeItem[]> => {
  const dynamic = hasContext(context)
  const key = dynamic ? '' : makerCode
  if (!dynamic && modelGroupsCache.has(key)) return Promise.resolve(modelGroupsCache.get(key)!)
  if (!dynamic && modelGroupsPromise.has(key)) return modelGroupsPromise.get(key)!

  const req = fetch(withContext(`/api/codes/model-groups?makerCode=${encodeURIComponent(makerCode)}`, context))
    .then(j)
    .then((data: CodeItem[]) => {
      if (!dynamic) modelGroupsCache.set(key, data)
      return data
    })
    .finally(() => {
      if (!dynamic) modelGroupsPromise.delete(key)
    })

  if (!dynamic) modelGroupsPromise.set(key, req)
  return req
}

export const getBodyTypes = (context?: CodeQueryContext): Promise<CodeItem[]> => {
  const dynamic = hasContext(context)
  if (!dynamic && bodyTypesCache) return Promise.resolve(bodyTypesCache)
  if (!dynamic && bodyTypesPromise) return bodyTypesPromise

  const req = fetch(withContext('/api/codes/body-types', context))
    .then(j)
    .then((data: CodeItem[]) => {
      if (!dynamic) bodyTypesCache = data
      return data
    })
    .finally(() => {
      if (!dynamic) bodyTypesPromise = null
    })

  if (!dynamic) bodyTypesPromise = req
  return req
}

export const getFuels = (context?: CodeQueryContext): Promise<CodeItem[]> =>
  fetch(withContext('/api/codes/fuels', context)).then(j)

export const getModels = (makerCode: string, modelGroupCode: string, context?: CodeQueryContext): Promise<CodeItem[]> => {
  const dynamic = hasContext(context)
  const key = dynamic ? '' : `${makerCode}|${modelGroupCode}`
  if (!dynamic && modelsCache.has(key)) return Promise.resolve(modelsCache.get(key)!)
  if (!dynamic && modelsPromise.has(key)) return modelsPromise.get(key)!

  const req = fetch(withContext(`/api/codes/models?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}`, context))
    .then(j)
    .then((data: CodeItem[]) => {
      if (!dynamic) modelsCache.set(key, data)
      return data
    })
    .finally(() => {
      if (!dynamic) modelsPromise.delete(key)
    })

  if (!dynamic) modelsPromise.set(key, req)
  return req
}
export const getTrims       = (makerCode: string, modelGroupCode: string, modelCode: string, context?: CodeQueryContext): Promise<CodeItem[]> =>
  fetch(withContext(`/api/codes/trims?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}`, context)).then(j)
export const getGrades      = (makerCode: string, modelGroupCode: string, modelCode: string, trimCode: string): Promise<CodeItem[]> =>
  fetch(`/api/codes/grades?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}&trimCode=${encodeURIComponent(trimCode)}`).then(j)
