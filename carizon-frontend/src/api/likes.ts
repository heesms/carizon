import type { CarListItem } from '@/api/cars'

const j = async (r: Response) => {
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  const json = await r.json()
  return json?.data ?? json
}

const CLIENT_ID_STORAGE_KEY = 'carizon:client_id'
const CLIENT_ID_COOKIE_NAME = 'carizon_client_id'
const CLIENT_ID_PATTERN = /^[A-Za-z0-9_-]{8,128}$/

const isBrowser = () => typeof window !== 'undefined' && typeof document !== 'undefined'

const readCookie = (name: string): string | null => {
  if (!isBrowser()) return null
  const found = document.cookie
    .split('; ')
    .find(row => row.startsWith(`${name}=`))
  if (!found) return null
  return decodeURIComponent(found.slice(name.length + 1))
}

const writeCookie = (name: string, value: string) => {
  if (!isBrowser()) return
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; Max-Age=31536000; SameSite=Lax`
}

const makeClientId = (): string => {
  const random = (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
    ? crypto.randomUUID().replace(/-/g, '')
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`
  return `cid_${random}`.slice(0, 40)
}

const getClientId = (): string => {
  if (!isBrowser()) return 'cid_server'

  let id: string | null = null
  try {
    id = localStorage.getItem(CLIENT_ID_STORAGE_KEY)
  } catch {
    id = null
  }
  if (!id) id = readCookie(CLIENT_ID_COOKIE_NAME)
  if (!id || !CLIENT_ID_PATTERN.test(id)) id = makeClientId()

  try { localStorage.setItem(CLIENT_ID_STORAGE_KEY, id) } catch {}
  writeCookie(CLIENT_ID_COOKIE_NAME, id)
  return id
}

const likeHeaders = (): HeadersInit => ({
  'X-Client-Id': getClientId(),
})

const WEEKLY_BEST_CACHE_TTL_MS = 60 * 60 * 1000
let weeklyBestCache: unknown[] | null = null
let weeklyBestCachedAt = 0
let weeklyBestPromise: Promise<unknown[]> | null = null

export type LikeInfo = { liked: boolean; count: number }
export type MyLikesInfo = { carIds: number[]; counts: Record<number, number> }
export type MyLikedCar = CarListItem & { likesCount?: number }

export const getLike    = (carId: number): Promise<LikeInfo> =>
  fetch(`/api/likes/${carId}`, { headers: likeHeaders() }).then(j)
export const toggleLike = (carId: number): Promise<LikeInfo> =>
  fetch(`/api/likes/${carId}`, { method: 'POST', headers: likeHeaders() }).then(j)
export const batchLikes = (carIds: number[]): Promise<Record<number, number>> =>
  fetch('/api/likes/batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...likeHeaders() },
    body: JSON.stringify({ carIds }),
  }).then(j)

export const getMyLikes = (limit = 100): Promise<MyLikesInfo> =>
  fetch(`/api/likes/me?limit=${encodeURIComponent(String(limit))}`, { headers: likeHeaders() }).then(j)

export const getMyLikedCars = (limit = 100): Promise<MyLikedCar[]> =>
  fetch(`/api/likes/me/cars?limit=${encodeURIComponent(String(limit))}`, { headers: likeHeaders() }).then(j)

export const getWeeklyBest = () => {
  const now = Date.now()
  if (weeklyBestCache && now - weeklyBestCachedAt < WEEKLY_BEST_CACHE_TTL_MS) {
    return Promise.resolve(weeklyBestCache)
  }
  if (weeklyBestPromise) return weeklyBestPromise
  weeklyBestPromise = fetch('/api/recommendation/weekly-best/home?limit=20')
    .then(j)
    .then((data: unknown) => {
      const rows = Array.isArray(data) ? data as unknown[] : []
      weeklyBestCache = rows
      weeklyBestCachedAt = Date.now()
      return rows
    })
    .finally(() => {
      weeklyBestPromise = null
    })
  return weeklyBestPromise
}
