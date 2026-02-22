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

let makersCache: CodeItem[] | null = null
let makersPromise: Promise<CodeItem[]> | null = null
const modelGroupsCache = new Map<string, CodeItem[]>()
const modelGroupsPromise = new Map<string, Promise<CodeItem[]>>()
const modelsCache = new Map<string, CodeItem[]>()
const modelsPromise = new Map<string, Promise<CodeItem[]>>()
let bodyTypesCache: CodeItem[] | null = null
let bodyTypesPromise: Promise<CodeItem[]> | null = null

export const getMakers = (): Promise<CodeItem[]> => {
  if (makersCache) return Promise.resolve(makersCache)
  if (makersPromise) return makersPromise

  makersPromise = fetch('/api/codes/makers')
    .then(j)
    .then((data: CodeItem[]) => {
      makersCache = data
      return data
    })
    .finally(() => {
      makersPromise = null
    })

  return makersPromise
}

export const getModelGroups = (makerCode: string): Promise<CodeItem[]> => {
  const key = makerCode
  if (modelGroupsCache.has(key)) return Promise.resolve(modelGroupsCache.get(key)!)
  if (modelGroupsPromise.has(key)) return modelGroupsPromise.get(key)!

  const req = fetch(`/api/codes/model-groups?makerCode=${encodeURIComponent(makerCode)}`)
    .then(j)
    .then((data: CodeItem[]) => {
      modelGroupsCache.set(key, data)
      return data
    })
    .finally(() => {
      modelGroupsPromise.delete(key)
    })

  modelGroupsPromise.set(key, req)
  return req
}

export const getBodyTypes = (): Promise<CodeItem[]> => {
  if (bodyTypesCache) return Promise.resolve(bodyTypesCache)
  if (bodyTypesPromise) return bodyTypesPromise

  bodyTypesPromise = fetch('/api/codes/body-types')
    .then(j)
    .then((data: CodeItem[]) => {
      bodyTypesCache = data
      return data
    })
    .finally(() => {
      bodyTypesPromise = null
    })

  return bodyTypesPromise
}

export const getModels = (makerCode: string, modelGroupCode: string): Promise<CodeItem[]> => {
  const key = `${makerCode}|${modelGroupCode}`
  if (modelsCache.has(key)) return Promise.resolve(modelsCache.get(key)!)
  if (modelsPromise.has(key)) return modelsPromise.get(key)!

  const req = fetch(`/api/codes/models?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}`)
    .then(j)
    .then((data: CodeItem[]) => {
      modelsCache.set(key, data)
      return data
    })
    .finally(() => {
      modelsPromise.delete(key)
    })

  modelsPromise.set(key, req)
  return req
}
export const getTrims       = (makerCode: string, modelGroupCode: string, modelCode: string): Promise<CodeItem[]> =>
  fetch(`/api/codes/trims?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}`).then(j)
export const getGrades      = (makerCode: string, modelGroupCode: string, modelCode: string, trimCode: string): Promise<CodeItem[]> =>
  fetch(`/api/codes/grades?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}&trimCode=${encodeURIComponent(trimCode)}`).then(j)
