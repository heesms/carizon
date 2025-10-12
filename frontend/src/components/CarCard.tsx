import React from 'react'
import { Link } from 'react-router-dom'
import type { CarListItem } from '@/services/api'

/** 로컬 이미지 경로(없으면 noimage) */
function modelImageUrl(modelCode?: string, fallback?: string){
    if (modelCode) return `/image/car/model/${modelCode}.webp`
    return fallback || '/image/car/noimage/noimage.png'
}

export default function CarCard({ item }: { item: CarListItem }){
    const [imgSrc, setImgSrc] = React.useState(
        modelImageUrl(item.modelCode, item.representativeImageUrl)
    )
    const price =
        item.priceMin && item.priceMax && item.priceMin!==item.priceMax
            ? `${item.priceMin.toLocaleString()} ~ ${item.priceMax.toLocaleString()} 원`
            : (item.priceMin ? `${item.priceMin.toLocaleString()} 원` : '가격정보 없음')

    return (
        <Link
            to={`/cars/${item.carId}`}
            className="group block bg-white rounded-2xl border hover:shadow-lg transition-shadow overflow-hidden"
        >
            <div className="aspect-[16/10] bg-gray-100 overflow-hidden">
                <img
                    src={imgSrc}
                    alt=""
                    className="h-full w-full object-cover group-hover:scale-[1.02] transition-transform"
                    loading="lazy"
                    onError={()=> setImgSrc('/image/car/noimage/noimage.png')}
                />
            </div>

            <div className="p-3 space-y-1.5">
                <div className="font-semibold leading-tight line-clamp-1">
                    {item.maker} {item.model}{item.trim?` ${item.trim}`:''}
                </div>
                <div className="text-sm text-gray-600">
                    {item.year ?? '-'} · {item.km?.toLocaleString() ?? '-'} km
                </div>
                <div className="text-[15px] font-bold">{price}</div>
            </div>
        </Link>
    )
}

