import React from 'react'
import { Link } from 'react-router-dom'

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
}

const NO_IMAGE = '/image/car/noimage/no_image.png'

function modelImageUrl(modelCode?: string, fallback?: string) {
    if (modelCode) return `/image/car/model/${modelCode}.webp`
    return fallback || NO_IMAGE
}

export default function CarCard({
    item,
    compact = true,
}: { item: CarListItem; compact?: boolean }) {
    if (!item) return null

    const [imgSrc, setImgSrc] = React.useState(
        modelImageUrl(item.modelCode, item.representativeImageUrl)
    )

    const price =
        item.priceMin && item.priceMax && item.priceMin !== item.priceMax
            ? `${item.priceMin.toLocaleString()} ~ ${item.priceMax.toLocaleString()}원`
            : (item.priceMin ? `${item.priceMin.toLocaleString()}원` : '가격정보 없음')

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
                        <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600">
                            {item.year && (
                                <span className="flex items-center gap-1">
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                    </svg>
                                    {item.year}년식
                                </span>
                            )}
                            {item.km && (
                                <span className="flex items-center gap-1">
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                    </svg>
                                    {item.km.toLocaleString()}km
                                </span>
                            )}
                        </div>
                    </div>
                    <div className="mt-2">
                        <div className="text-xl font-bold text-blue-600">{price}</div>
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
                <div className="flex items-center gap-3 text-sm text-gray-600">
                    {item.year && <span>{item.year}년식</span>}
                    {item.km && <span>{item.km.toLocaleString()}km</span>}
                </div>
                <div className="text-xl font-bold text-blue-600">{price}</div>
            </div>
        </Link>
    )
}
