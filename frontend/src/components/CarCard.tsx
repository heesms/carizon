import React from 'react'
import { Link } from 'react-router-dom'

// Search.tsx에서 쓴 타입을 그대로 복사(또는 import)
// (여기서 다시 선언해도 무방)
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

const NO_IMAGE = '/image/car/noimage/no_image.png' // public/ 아래 경로

function modelImageUrl(modelCode?: string, fallback?: string) {
    if (modelCode) return `/image/car/model/${modelCode}.webp`
    return fallback || NO_IMAGE
}

export default function CarCard({
                                    item,
                                    compact = true,
                                }: { item: CarListItem; compact?: boolean }) {

    // 방어: item이 비어있을 일은 거의 없지만 혹시 몰라 체크
    if (!item) return null

    const [imgSrc, setImgSrc] = React.useState(
        modelImageUrl(item.modelCode, item.representativeImageUrl)
    )

    const price =
        item.priceMin && item.priceMax && item.priceMin !== item.priceMax
            ? `${item.priceMin.toLocaleString()} ~ ${item.priceMax.toLocaleString()} 원`
            : (item.priceMin ? `${item.priceMin.toLocaleString()} 원` : '가격정보 없음')

    if (compact) {
        return (
            <Link to={`/cars/${item.carId}`} className="group flex gap-3 bg-white rounded-xl border p-3 hover:shadow transition">
                <div className="w-28 h-20 bg-gray-100 overflow-hidden rounded">
                    <img
                        src={imgSrc}
                        alt=""
                        className="w-full h-full object-cover"
                        loading="lazy"
                        onError={() => setImgSrc(NO_IMAGE)}
                    />
                </div>
                <div className="min-w-0">
                    <div className="font-semibold leading-tight line-clamp-1">
                        {item.maker} {item.model}{item.trim ? ` ${item.trim}` : ''}
                    </div>
                    <div className="text-xs text-gray-600">
                        {item.year ?? '-'} · {item.km?.toLocaleString() ?? '-'} km
                    </div>
                    <div className="text-sm font-bold mt-0.5">{price}</div>
                </div>
            </Link>
        )
    }

    // (옵션) 큰 카드 모드
    return (
        <Link to={`/cars/${item.carId}`} className="group block bg-white rounded-2xl border hover:shadow-lg transition-shadow overflow-hidden">
            <div className="aspect-[16/10] bg-gray-100 overflow-hidden">
                <img
                    src={imgSrc}
                    alt=""
                    className="h-full w-full object-cover group-hover:scale-[1.02] transition-transform"
                    loading="lazy"
                    onError={() => setImgSrc(NO_IMAGE)}
                />
            </div>
            <div className="p-3 space-y-1.5">
                <div className="font-semibold leading-tight line-clamp-1">
                    {item.maker} {item.model}{item.trim ? ` ${item.trim}` : ''}
                </div>
                <div className="text-sm text-gray-600">
                    {item.year ?? '-'} · {item.km?.toLocaleString() ?? '-'} km
                </div>
                <div className="text-[15px] font-bold">{price}</div>
            </div>
        </Link>
    )
}
