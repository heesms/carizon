import React from 'react'
import { Link } from 'react-router-dom'
import { getCachedImageSource } from '@/utils/imageCache'

type CarListItem = {
    carId: number
    maker: string
    model: string
    trim?: string
    year?: number
    km?: number
    priceMin?: number
    priceMax?: number
    representativeImageUrl?: string
    modelCode?: string
    fuel?: string
    region?: string
}

const NO_IMAGE = '/image/car/noimage/no_image.png'

/** 리스트 이미지: representativeImageUrl 우선, 없으면 모델 이미지, 최후 noimage */
function listImageUrl(representativeImageUrl?: string, modelCode?: string) {
    if (representativeImageUrl && representativeImageUrl.trim() !== '') return representativeImageUrl
    if (modelCode) return `/image/car/model/${modelCode}.webp`
    return NO_IMAGE
}

export default function CarCard({
    item,
    compact = true,
}: { item: CarListItem; compact?: boolean }) {
    if (!item) return null
    if (item.priceMin == null && item.priceMax == null) return null

    const preferredImageSrc = React.useMemo(
        () => listImageUrl(item.representativeImageUrl, item.modelCode),
        [item.representativeImageUrl, item.modelCode]
    )
    const [imgSrc, setImgSrc] = React.useState(preferredImageSrc)

    React.useEffect(() => {
        let cancelled = false
        setImgSrc(preferredImageSrc)
        getCachedImageSource(preferredImageSrc)
            .then((cached) => {
                if (!cancelled) setImgSrc(cached || preferredImageSrc)
            })
            .catch(() => {
                if (!cancelled) setImgSrc(preferredImageSrc)
            })
        return () => {
            cancelled = true
        }
    }, [preferredImageSrc])

    const price =
        item.priceMin != null && item.priceMax != null && item.priceMin !== item.priceMax
            ? `${item.priceMin.toLocaleString()} ~ ${item.priceMax.toLocaleString()}만원`
            : (item.priceMin != null ? `${item.priceMin.toLocaleString()}만원` : item.priceMax != null ? `${item.priceMax.toLocaleString()}만원` : null)

    if (compact) {
        return (
            <Link 
                to={`/cars/${item.carId}`} 
                className="group modern-card p-4 flex gap-4 hover-lift animate-fade-in"
            >
                <div className="w-32 h-24 sm:w-36 sm:h-28 bg-gradient-to-br from-gray-100 to-gray-200 overflow-hidden rounded-xl flex-shrink-0 shadow-inner">
                    <img
                        src={imgSrc}
                        alt={`${item.maker} ${item.model}`}
                        className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                        loading="lazy"
                        onError={() => setImgSrc(NO_IMAGE)}
                    />
                </div>
                <div className="min-w-0 flex-1 flex flex-col justify-between">
                    <div>
                        <h3 className="font-bold text-lg leading-tight line-clamp-2 group-hover:text-blue-600 transition mb-1">
                            {item.maker} {item.model}{item.trim ? ` ${item.trim}` : ''}
                        </h3>
                        {/* 1행: 연식, 키로수 */}
                        <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600 mb-1">
                            {item.year && (
                                <span className="flex items-center gap-1">
                                    <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                    </svg>
                                    {item.year}년식
                                </span>
                            )}
                            {item.km != null && item.km !== 0 && (
                                <span className="flex items-center gap-1">
                                    <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                    </svg>
                                    {item.km.toLocaleString()}km
                                </span>
                            )}
                        </div>
                        {/* 2행: 연료, 지역 (아이콘 포함) */}
                        <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600">
                            <span className="flex items-center gap-1">
                                <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7v1a2 2 0 01-2 2h-1V6a2 2 0 00-2-2H6a2 2 0 00-2 2v4H5a2 2 0 01-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 01-2-2h-1V7a2 2 0 012-2z" />
                                </svg>
                                {item.fuel || '-'}
                            </span>
                            <span className="flex items-center gap-1">
                                <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L12 21.314l-5.657-4.657a8 8 0 1111.314 0z" />
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15a3 3 0 100-6 3 3 0 000 6z" />
                                </svg>
                                {item.region || '-'}
                            </span>
                        </div>
                    </div>
                    <div className="mt-2">
                        {price != null && <div className="text-xl font-bold text-blue-600">{price}</div>}
                    </div>
                </div>
                <div className="flex-shrink-0 self-center opacity-0 group-hover:opacity-100 transition-opacity">
                    <svg className="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                    </svg>
                </div>
            </Link>
        )
    }

    // 큰 카드 모드
    return (
        <Link 
            to={`/cars/${item.carId}`} 
            className="group block modern-card overflow-hidden hover-lift"
        >
            <div className="aspect-[16/10] bg-gradient-to-br from-gray-100 to-gray-200 overflow-hidden relative">
                <img
                    src={imgSrc}
                    alt={`${item.maker} ${item.model}`}
                    className="h-full w-full object-cover group-hover:scale-110 transition-transform duration-500"
                    loading="lazy"
                    onError={() => setImgSrc(NO_IMAGE)}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent"></div>
            </div>
            <div className="p-4 space-y-2">
                <h3 className="font-bold text-lg leading-tight line-clamp-1 group-hover:text-blue-600 transition">
                    {item.maker} {item.model}{item.trim ? ` ${item.trim}` : ''}
                </h3>
                {/* 1행: 연식, 키로수 */}
                <div className="flex flex-wrap items-center gap-3 text-sm text-gray-600">
                    {item.year && (
                        <span className="flex items-center gap-1">
                            <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                            {item.year}년식
                        </span>
                    )}
                    {item.km != null && item.km !== 0 && (
                        <span className="flex items-center gap-1">
                            <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                            </svg>
                            {item.km.toLocaleString()}km
                        </span>
                    )}
                </div>
                {/* 2행: 연료, 지역 (아이콘 포함) */}
                <div className="flex flex-wrap items-center gap-3 text-sm text-gray-600">
                    <span className="flex items-center gap-1">
                        <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7v1a2 2 0 01-2 2h-1V6a2 2 0 00-2-2H6a2 2 0 00-2 2v4H5a2 2 0 01-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 01-2-2h-1V7a2 2 0 012-2z" />
                        </svg>
                        {item.fuel || '-'}
                    </span>
                    <span className="flex items-center gap-1">
                        <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L12 21.314l-5.657-4.657a8 8 0 1111.314 0z" />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15a3 3 0 100-6 3 3 0 000 6z" />
                        </svg>
                        {item.region || '-'}
                    </span>
                </div>
                {price != null && <div className="text-xl font-bold text-blue-600">{price}</div>}
            </div>
        </Link>
    )
}
