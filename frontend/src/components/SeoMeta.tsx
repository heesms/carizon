import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

type SeoMetaProps = {
    title: string
    description: string
    keywords?: string
    canonicalPath?: string
    noindex?: boolean
    ogType?: string
    ogImage?: string
}

function upsertMeta(selector: string, value?: string, attribute = 'name') {
    if (typeof document === 'undefined') return

    const existing = document.querySelector<HTMLMetaElement>(`meta[${attribute}="${selector}"]`)
    if (!value) {
        existing?.remove()
        return
    }

    if (existing) {
        existing.setAttribute('content', value)
        return
    }

    const meta = document.createElement('meta')
    meta.setAttribute(attribute, selector)
    meta.setAttribute('content', value)
    document.head.appendChild(meta)
}

function upsertLinkRel(rel: string, href?: string) {
    if (typeof document === 'undefined') return

    const existing = document.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`)
    if (!href) {
        existing?.remove()
        return
    }

    if (existing) {
        existing.setAttribute('href', href)
        return
    }

    const link = document.createElement('link')
    link.setAttribute('rel', rel)
    link.setAttribute('href', href)
    document.head.appendChild(link)
}

export default function SeoMeta({
    title,
    description,
    keywords,
    canonicalPath,
    noindex = false,
    ogType = 'website',
    ogImage,
}: SeoMetaProps) {
    const location = useLocation()

    useEffect(() => {
        if (typeof document === 'undefined') return

        const canonical = canonicalPath
            ? `${window.location.origin}${canonicalPath}`
            : `${window.location.origin}${location.pathname}${location.search}`

        document.title = title

        upsertMeta('description', description)
        if (keywords) upsertMeta('keywords', keywords)
        upsertMeta('robots', noindex ? 'noindex, follow' : 'index, follow')

        upsertMeta('og:title', title, 'property')
        upsertMeta('og:description', description, 'property')
        upsertMeta('og:url', canonical, 'property')
        upsertMeta('og:type', ogType, 'property')
        if (ogImage) upsertMeta('og:image', ogImage, 'property')
        upsertMeta('og:site_name', 'Carizon', 'property')
        upsertMeta('og:locale', 'ko_KR', 'property')

        upsertMeta('twitter:card', 'summary_large_image')
        upsertMeta('twitter:title', title, 'name')
        upsertMeta('twitter:description', description, 'name')
        if (ogImage) upsertMeta('twitter:image', ogImage, 'name')

        upsertLinkRel('canonical', canonical)
    }, [title, description, keywords, canonicalPath, noindex, ogType, ogImage, location.pathname, location.search])

    return null
}
