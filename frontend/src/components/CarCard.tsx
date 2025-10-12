import React from 'react'
import { Link } from 'react-router-dom'
import type { CarListItem } from '@/services/api'

export default function CarCard({ item }: { item: CarListItem }){
  const price =
    item.priceMin && item.priceMax && item.priceMin !== item.priceMax
      ? `${item.priceMin.toLocaleString()} ~ ${item.priceMax.toLocaleString()} 원`
      : (item.priceMin ? `${item.priceMin.toLocaleString()} 원` : '가격정보 없음')

  return (
    <div className="bg-white border rounded-xl overflow-hidden flex">
      <div className="w-40 h-28 bg-gray-100 overflow-hidden">
        {item.representativeImageUrl ? (
          <img src={item.representativeImageUrl} alt="" className="w-full h-full object-cover"/>
        ) : (
          <div className="w-full h-full grid place-items-center text-xs text-gray-400">no image</div>
        )}
      </div>
      <div className="flex-1 p-3">
        <Link to={`/cars/${item.carId}`} className="font-semibold hover:underline">
          {item.maker} {item.model}{item.trim ? ` ${item.trim}` : ''}
        </Link>
        <div className="text-sm text-gray-600 mt-1">{item.year ?? '-'} · {item.km?.toLocaleString() ?? '-'} km</div>
        <div className="mt-2 font-semibold">{price}</div>
      </div>
    </div>
  )
}
