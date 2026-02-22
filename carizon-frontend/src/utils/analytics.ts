const GA_MEASUREMENT_ID = String(import.meta.env.VITE_GA_MEASUREMENT_ID || '').trim()

type GtagArgs = [string, ...unknown[]]

declare global {
  interface Window {
    dataLayer: unknown[][]
    gtag: (...args: GtagArgs) => void
  }
}

const isValidId = (value: string) => {
  const trimmed = value.trim()
  if (!trimmed) return false
  if (/^REPLACE_WITH/i.test(trimmed)) return false
  if (/^YOUR_/i.test(trimmed)) return false
  return true
}

let initialized = false

const gaEnabled = isValidId(GA_MEASUREMENT_ID)

export const initGoogleAnalytics = () => {
  if (typeof window === 'undefined' || !gaEnabled || initialized) return

  window.dataLayer = window.dataLayer || []
  window.gtag = (...args: GtagArgs) => window.dataLayer.push(args)

  const existing = document.getElementById('carizon-ga-script')
  if (!existing) {
    const script = document.createElement('script')
    script.id = 'carizon-ga-script'
    script.async = true
    script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`
    document.head.appendChild(script)
  }

  window.gtag('js', new Date())
  window.gtag('config', GA_MEASUREMENT_ID, { send_page_view: false })
  initialized = true
}

export const trackPageView = (pathname: string, pageTitle?: string) => {
  if (!gaEnabled || typeof window === 'undefined') return
  if (typeof window.gtag !== 'function') return
  const fullPath = pathname.startsWith('http')
    ? pathname
    : `${window.location.origin}${pathname}`
  window.gtag('event', 'page_view', {
    page_title: pageTitle ?? document.title,
    page_location: fullPath,
    page_path: pathname,
  })
}
