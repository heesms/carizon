const j = async (r: Response) => {
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const json = await r.json()
  return json?.data ?? json
}

type RawCodeItem = Record<string, unknown>

type FilterValue = string | number | boolean | undefined | null

type CodeQuery = Record<string, FilterValue>

export type CodeItem = {
  code: string
  name: string
  countryCode?: string
  countryName?: string
  domestic?: number | boolean
  carCount?: number
}

const getAny = (obj: RawCodeItem, keys: string[]) =>
  keys.map(k => obj[k]).find(v => v !== undefined && v !== null && String(v).trim() !== '')

const normalizeText = (v: unknown): string | undefined => {
  if (v === undefined || v === null) return undefined
  const s = String(v).trim()
  return s === '' ? undefined : s
}

const parseCodeItemString = (obj: RawCodeItem, keys: string[]): string | undefined => {
  const found = getAny(obj, keys)
  if (found === undefined) return undefined
  return String(found).trim()
}

const parseCodeItemNumberLike = (value: unknown): number | undefined => {
  if (value === undefined || value === null) return undefined
  const normalized = String(value).replace(/,/g, '').trim()
  const n = Number(normalized)
  return Number.isFinite(n) ? n : undefined
}

const parseCodeItemNumber = (obj: RawCodeItem, keys: string[]): number | undefined => {
  const found = getAny(obj, keys)
  if (found === undefined) return undefined
  return parseCodeItemNumberLike(found)
}

const parseCodeItemBoolean = (obj: RawCodeItem, keys: string[]): number | boolean | undefined => {
  const found = getAny(obj, keys)
  if (found === undefined) return undefined
  if (found === true || found === false || found === 1 || found === 0) return found
  const str = String(found).trim().toUpperCase()
  if (!str) return undefined
  if (['1', 'Y', 'YES', 'TRUE', 'T'].includes(str)) return true
  if (['0', 'N', 'NO', 'FALSE', 'F'].includes(str)) return false
  return undefined
}

const normalizeCodeItem = (item: unknown): CodeItem => {
  const raw = item as RawCodeItem
  return {
    code: parseCodeItemString(raw, ['code', 'CODE', 'Code']) || '',
    name: parseCodeItemString(raw, ['name', 'NAME', 'Name']) || '',
    countryCode: normalizeText(parseCodeItemString(raw, [
      'countryCode',
      'country_code',
      'COUNTRY_CODE',
      'COUNTRYCODE',
      'country',
      'COUNTRY',
    ])),
    countryName: normalizeText(parseCodeItemString(raw, [
      'countryName',
      'country_name',
      'COUNTRY_NAME',
      'countryname',
      'COUNTRY_NAME_KR',
      'COUNTRY',
    ])),
    domestic: parseCodeItemBoolean(raw, [
      'domestic',
      'DOMESTIC',
      'isDomestic',
      'IS_DOMESTIC',
      'isdirect',
      'isDomesticCar',
      'is_domestic',
    ]) ?? undefined,
    carCount: parseCodeItemNumber(raw, ['carCount', 'car_count', 'CARCOUNT', 'CAR_COUNT', 'COUNT']) ,
  }
}

const normalizeCodeItems = (items: unknown): CodeItem[] =>
  Array.isArray(items)
    ? items.map(normalizeCodeItem)
    : []

const normalizeQuery = (filters?: CodeQuery) => {
  if (!filters) return ''
  const usp = new URLSearchParams()
  Object.keys(filters).sort().forEach((k) => {
    const value = filters[k]
    if (value === undefined || value === null) return
    const str = String(value).trim()
    if (str === '') return
    usp.append(k, str)
  })
  return usp.toString()
}

let makersCache: Record<string, CodeItem[]> = {}
let makersPromise: Map<string, Promise<CodeItem[]>> = new Map()
const modelGroupsCache: Map<string, CodeItem[]> = new Map()
const modelGroupsPromise: Map<string, Promise<CodeItem[]>> = new Map()
const modelsCache: Map<string, CodeItem[]> = new Map()
const modelsPromise: Map<string, Promise<CodeItem[]>> = new Map()
const trimsCache: Map<string, CodeItem[]> = new Map()
const trimsPromise: Map<string, Promise<CodeItem[]>> = new Map()
const bodyTypesCache: Record<string, CodeItem[]> = {}
const bodyTypesPromise: Map<string, Promise<CodeItem[]>> = new Map()
const fuelsCache: Record<string, CodeItem[]> = {}
const fuelsPromise: Map<string, Promise<CodeItem[]>> = new Map()

const cacheKey = (path: string, filters?: CodeQuery) => `${path}?${normalizeQuery(filters)}`

export const getMakers = (filters?: CodeQuery): Promise<CodeItem[]> => {
  const key = cacheKey('makers', filters)
  if (makersCache[key]) return Promise.resolve(makersCache[key])
  if (makersPromise.has(key)) return makersPromise.get(key)!

  const req = fetch(`/api/codes/makers?${normalizeQuery(filters)}`)
    .then(j)
    .then(normalizeCodeItems)
    .then((data: CodeItem[]) => {
      makersCache[key] = data
      return data
    })
    .finally(() => {
      makersPromise.delete(key)
    })

  makersPromise.set(key, req)
  return req
}

export const getModelGroups = (makerCode: string, filters?: CodeQuery): Promise<CodeItem[]> => {
  const key = cacheKey(`model-groups:${makerCode}`, filters)
  if (modelGroupsCache.has(key)) return Promise.resolve(modelGroupsCache.get(key)!)
  if (modelGroupsPromise.has(key)) return modelGroupsPromise.get(key)!

  const query = normalizeQuery({ ...(filters ?? {}), makerCode })
  const req = fetch(`/api/codes/model-groups?${query}`)
    .then(j)
    .then(normalizeCodeItems)
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

export const getModels = (makerCode: string, modelGroupCode: string, filters?: CodeQuery): Promise<CodeItem[]> => {
  const key = cacheKey(`models:${makerCode}:${modelGroupCode}`, filters)
  if (modelsCache.has(key)) return Promise.resolve(modelsCache.get(key)!)
  if (modelsPromise.has(key)) return modelsPromise.get(key)!

  const query = normalizeQuery({ ...(filters ?? {}), makerCode, modelGroupCode })
  const req = fetch(`/api/codes/models?${query}`)
    .then(j)
    .then(normalizeCodeItems)
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

export const getTrims = (makerCode: string, modelGroupCode: string, modelCode: string, filters?: CodeQuery): Promise<CodeItem[]> => {
  const key = cacheKey(`trims:${makerCode}:${modelGroupCode}:${modelCode}`, filters)
  if (trimsCache.has(key)) return Promise.resolve(trimsCache.get(key)!)
  if (trimsPromise.has(key)) return trimsPromise.get(key)!

  const query = normalizeQuery({ ...(filters ?? {}), makerCode, modelGroupCode, modelCode })
  const req = fetch(`/api/codes/trims?${query}`).then(j)
    .then(normalizeCodeItems)
    .then((data: CodeItem[]) => {
      trimsCache.set(key, data)
      return data
    })
    .finally(() => {
      trimsPromise.delete(key)
    })

  trimsPromise.set(key, req)
  return req
}

export const getGrades = (makerCode: string, modelGroupCode: string, modelCode: string, trimCode: string): Promise<CodeItem[]> =>
  fetch(`/api/codes/grades?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}&trimCode=${encodeURIComponent(trimCode)}`).then(j)

export const getBodyTypes = (filters?: CodeQuery): Promise<CodeItem[]> => {
  const key = cacheKey('body-types', filters)
  if (bodyTypesCache[key]) return Promise.resolve(bodyTypesCache[key])
  if (bodyTypesPromise.has(key)) return bodyTypesPromise.get(key)!

  const req = fetch(`/api/codes/body-types?${normalizeQuery(filters)}`)
    .then(j)
    .then(normalizeCodeItems)
    .then((data: CodeItem[]) => {
      bodyTypesCache[key] = data
      return data
    })
    .finally(() => {
      bodyTypesPromise.delete(key)
    })

  bodyTypesPromise.set(key, req)
  return req
}

export const getFuels = (filters?: CodeQuery): Promise<CodeItem[]> => {
  const key = cacheKey('fuels', filters)
  if (fuelsCache[key]) return Promise.resolve(fuelsCache[key])
  if (fuelsPromise.has(key)) return fuelsPromise.get(key)!

  const req = fetch(`/api/codes/fuels?${normalizeQuery(filters)}`)
    .then(j)
    .then(normalizeCodeItems)
    .then((data: CodeItem[]) => {
      fuelsCache[key] = data
      return data
    })
    .finally(() => {
      fuelsPromise.delete(key)
    })

  fuelsPromise.set(key, req)
  return req
}
