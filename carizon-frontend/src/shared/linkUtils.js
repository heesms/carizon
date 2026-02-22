export const getDomain = (url) => {
    try { return new URL(url).hostname.replace(/^www\./,'') } catch { return '' }
}

export const faviconUrl = (url) => {
    const d = getDomain(url)
    return d ? `https://www.google.com/s2/favicons?sz=64&domain=${encodeURIComponent(d)}` : ''
}

export const trimTitle = (t, n=60) => (t && t.length>n) ? (t.slice(0,n)+'…') : (t||'')
