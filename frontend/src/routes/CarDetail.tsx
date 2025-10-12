import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getCarDetail, getPriceHistory, type CarDetail as CarDetailType, type PricePoint } from '@/services/api'
import PriceChart from '@/components/PriceChart'

function bigImageUrl(modelCode?: string, fallback?: string){
    if (modelCode) return `/image/car/model/${modelCode}.webp`
    return fallback || '/image/car/noimage/noimage.png'
}

export default function CarDetail(){
    const { id } = useParams()
    const [detail, setDetail] = useState<CarDetailType | null>(null)
    const [platformCarId, setPlatformCarId] = useState<number | undefined>(undefined)
    const [history, setHistory] = useState<PricePoint[]>([])
    const [bigSrc, setBigSrc] = useState<string>('/image/car/noimage/noimage.png')

    useEffect(()=>{
        if(!id) return
        getCarDetail(id).then(d=>{
            setDetail(d)
            // modelCode 내려주면 큰 이미지 표시
            const mc = (d as any).specs?.modelCode as string | undefined
            const fallback = (d as any).representativeImageUrl as string | undefined
            setBigSrc(bigImageUrl(mc, fallback))
        }).catch(()=>setDetail(null))
    },[id])

    useEffect(()=>{
        if (!id) return
        getPriceHistory(id, platformCarId).then(res=> setHistory(res.points||[])).catch(()=>setHistory([]))
    }, [id, platformCarId])

    const platforms = detail?.platforms || []

    return (
        <div className="space-y-6">
            {/* 상단 히어로 영역: 큰 이미지 */}
            <section className="bg-white border rounded-xl p-4">
                <div className="flex flex-col lg:flex-row gap-4">
                    <div className="w-full lg:w-[480px] h-[280px] bg-gray-100 overflow-hidden rounded">
                        <img
                            src={bigSrc}
                            alt=""
                            className="w-full h-full object-cover"
                            onError={()=> setBigSrc('/image/car/noimage/noimage.png')}
                        />
                    </div>
                    <div className="flex-1">
                        <h1 className="text-xl font-bold">차량 상세</h1>
                        {detail && (
                            <div className="text-sm text-gray-700 mt-1">
                                {detail.specs.maker} {detail.specs.model} {detail.specs.trim} · {detail.specs.year} · {detail.specs.km?.toLocaleString()} km
                            </div>
                        )}
                        {/* 가격 요약/배지 등 확장 포인트 */}
                    </div>
                </div>
            </section>

            {/* 플랫폼별 링크 + 가격 비교 */}
            <section className="bg-white border rounded-xl p-4">
                <h2 className="font-semibold mb-3">플랫폼별 매물 / 가격 비교</h2>
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm">
                        <thead className="text-gray-500">
                        <tr>
                            <th className="text-left p-2">선택</th>
                            <th className="text-left p-2">플랫폼</th>
                            <th className="text-left p-2">가격</th>
                            <th className="text-left p-2">상태</th>
                            <th className="text-left p-2">원문 링크</th>
                            <th className="text-left p-2">최근 관측</th>
                        </tr>
                        </thead>
                        <tbody>
                        {platforms.map(p=>(
                            <tr key={p.platformCarId} className="border-t">
                                <td className="p-2">
                                    <input
                                        type="radio"
                                        name="pcid"
                                        onChange={()=>setPlatformCarId(p.platformCarId)}
                                        checked={platformCarId===p.platformCarId}
                                    />
                                </td>
                                <td className="p-2">{p.platform}</td>
                                <td className="p-2 font-medium">{p.price?.toLocaleString()} 원</td>
                                <td className="p-2">{p.status}</td>
                                <td className="p-2">{p.pcUrl ? <a href={p.pcUrl} target="_blank" className="text-blue-600 underline">바로가기</a> : '-'}</td>
                                <td className="p-2">{p.lastSeenDate || '-'}</td>
                            </tr>
                        ))}
                        {platforms.length===0 && <tr><td className="p-2 text-gray-500" colSpan={6}>플랫폼 데이터가 없습니다.</td></tr>}
                        </tbody>
                    </table>
                </div>
            </section>

            {/* 가격 히스토리 */}
            <section className="bg-white border rounded-xl p-4">
                <h2 className="font-semibold mb-3">가격 히스토리</h2>
                <PriceChart points={history} />
            </section>

            {/* 매물 정보 (스펙) */}
            {detail && (
                <section className="bg-white border rounded-xl p-4">
                    <h2 className="font-semibold mb-3">매물 정보</h2>
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-2 text-sm">
                        <Spec label="제조사" value={detail.specs.maker} />
                        <Spec label="모델" value={detail.specs.model} />
                        <Spec label="트림" value={detail.specs.trim} />
                        <Spec label="연식" value={detail.specs.year?.toString()} />
                        <Spec label="주행거리" value={detail.specs.km ? `${detail.specs.km.toLocaleString()} km` : undefined} />
                        <Spec label="연료" value={detail.specs.fuel} />
                        <Spec label="변속기" value={detail.specs.transmission} />
                        <Spec label="차종" value={detail.specs.bodyType} />
                        <Spec label="지역" value={detail.specs.region} />
                    </div>
                </section>
            )}
        </div>
    )
}

function Spec({label, value}:{label:string; value?:string}){
    return (
        <div className="flex justify-between gap-4">
            <span className="text-gray-500">{label}</span>
            <span className="font-medium text-right">{value ?? '-'}</span>
        </div>
    )
}
