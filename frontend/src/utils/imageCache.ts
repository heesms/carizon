const IMAGE_CACHE_NAME = 'carizon-image-cache-v1'
const IMAGE_CACHE_META_KEY = 'carizon:image-cache-meta:v1'
const IMAGE_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000
const IMAGE_CACHE_MAX_ENTRIES = 200

type CacheMeta = Record<string, number>

const objectUrlMemo = new Map<string, string>()
const inflightMemo = new Map<string, Promise<string>>()

function canUseBrowserCache(): boolean {
    return typeof window !== 'undefined' && typeof window.caches !== 'undefined'
}

function isBypassSource(src: string): boolean {
    return !src || src.startsWith('blob:') || src.startsWith('data:')
}

function canonicalUrl(src: string): string | null {
    if (typeof window === 'undefined') return null
    if (isBypassSource(src)) return null
    try {
        return new URL(src, window.location.origin).toString()
    } catch {
        return null
    }
}

function loadMeta(): CacheMeta {
    if (typeof window === 'undefined') return {}
    try {
        const raw = window.localStorage.getItem(IMAGE_CACHE_META_KEY)
        if (!raw) return {}
        const parsed = JSON.parse(raw) as unknown
        return typeof parsed === 'object' && parsed !== null ? (parsed as CacheMeta) : {}
    } catch {
        return {}
    }
}

function saveMeta(meta: CacheMeta): void {
    if (typeof window === 'undefined') return
    try {
        window.localStorage.setItem(IMAGE_CACHE_META_KEY, JSON.stringify(meta))
    } catch {
        // ignore storage failures
    }
}

function isExpired(savedAt?: number): boolean {
    if (!savedAt) return true
    return Date.now() - savedAt > IMAGE_CACHE_TTL_MS
}

async function trimOldEntries(cache: Cache, meta: CacheMeta): Promise<CacheMeta> {
    const entries = Object.entries(meta).sort((a, b) => b[1] - a[1])
    if (entries.length <= IMAGE_CACHE_MAX_ENTRIES) return meta

    const nextMeta: CacheMeta = {}
    for (let i = 0; i < entries.length; i += 1) {
        const [key, savedAt] = entries[i]
        if (i < IMAGE_CACHE_MAX_ENTRIES) {
            nextMeta[key] = savedAt
            continue
        }
        try {
            await cache.delete(key)
        } catch {
            // ignore delete errors
        }
    }
    return nextMeta
}

async function responseToObjectUrl(cacheKey: string, response: Response): Promise<string> {
    const cached = objectUrlMemo.get(cacheKey)
    if (cached) return cached
    const blob = await response.blob()
    if (!blob || blob.size === 0) return cacheKey
    const objectUrl = URL.createObjectURL(blob)
    objectUrlMemo.set(cacheKey, objectUrl)
    return objectUrl
}

export async function getCachedImageSource(src: string): Promise<string> {
    if (!canUseBrowserCache() || isBypassSource(src)) return src
    const key = canonicalUrl(src)
    if (!key) return src

    const memoized = objectUrlMemo.get(key)
    if (memoized) return memoized

    const pending = inflightMemo.get(key)
    if (pending) return pending

    const task = (async () => {
        const cache = await window.caches.open(IMAGE_CACHE_NAME)
        const meta = loadMeta()
        const savedAt = meta[key]
        const cachedResponse = await cache.match(key)

        if (cachedResponse && !isExpired(savedAt)) {
            meta[key] = Date.now()
            saveMeta(meta)
            return responseToObjectUrl(key, cachedResponse)
        }

        try {
            const network = await fetch(key, { mode: 'cors', credentials: 'omit', cache: 'no-store' })
            if (network.ok) {
                await cache.put(key, network.clone())
                meta[key] = Date.now()
                const trimmed = await trimOldEntries(cache, meta)
                saveMeta(trimmed)
                return responseToObjectUrl(key, network)
            }
        } catch {
            // ignore network errors and try stale cache below
        }

        if (cachedResponse) {
            meta[key] = Date.now()
            saveMeta(meta)
            return responseToObjectUrl(key, cachedResponse)
        }

        return src
    })()
        .catch(() => src)
        .finally(() => {
            inflightMemo.delete(key)
        })

    inflightMemo.set(key, task)
    return task
}

