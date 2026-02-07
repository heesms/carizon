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
    const [loading, setLoading] = useState(true)

    useEffect(()=>{
        if(!id) return
        setLoading(true)
        getCarDetail(id).then(response=>{
            // 백엔드 응답 구조: { success: true, data: { carId, content: [...] } }
            const rawData = (response as any).data || response
            if (!rawData || !rawData.content || !Array.isArray(rawData.content) || rawData.content.length === 0) {
                setDetail(null)
                return
            }
            
            // content 배열을 specs와 platforms로 변환
            const firstRow = rawData.content[0]
            const specs = {
                maker: firstRow.makerName || '',
                model: firstRow.modelName || '',
                modelGroup: firstRow.modelGroupName || '',
                trim: firstRow.trimName || '',
                year: firstRow.year || null,
                km: firstRow.mileage || null,
                fuel: firstRow.fuel || '',
                transmission: firstRow.transmission || '',
                color: firstRow.color || '',
                bodyType: firstRow.bodyType || '',
                region: firstRow.region || '',
                displacement: firstRow.displacement || null,
            }
            
            // platforms 배열 생성 (중복 제거)
            const platformMap = new Map()
            rawData.content.forEach((row: any) => {
                if (row.platformCarId) {
                    platformMap.set(row.platformCarId, {
                        platformCarId: row.platformCarId,
                        platform: row.platformName || '',
                        price: row.price || null,
                        status: row.status || '',
                        pcUrl: row.pcUrl || null,
                        mUrl: row.mUrl || null,
                        lastSeenDate: row.lastSeenDate || null,
                    })
                }
            })
            
            const d: CarDetailType = {
                carId: rawData.carId || Number(id),
                specs,
                platforms: Array.from(platformMap.values()),
                recommended: [],
            }
            
            setDetail(d)
            const mc = (d as any).specs?.modelCode as string | undefined
            const fallback = (d as any).representativeImageUrl as string | undefined
            setBigSrc(bigImageUrl(mc, fallback))
        }).catch((err) => {
            console.error('Failed to load car detail:', err)
            setDetail(null)
        })
        .finally(() => setLoading(false))
    },[id])

    useEffect(()=>{
        if (!id) return
        getPriceHistory(id, platformCarId)
            .then(res => {
                // 백엔드 응답 구조: { success: true, data: { points: [...] } } 또는 { points: [...] }
                const data = (res as any).data || res
                setHistory(data?.points || [])
            })
            .catch((err) => {
                console.error('Failed to load price history:', err)
                setHistory([])
            })
    }, [id, platformCarId])

    const platforms = detail?.platforms || []
    const minPrice = platforms.length > 0 ? Math.min(...platforms.filter(p => p.price).map(p => p.price!)) : null
    const maxPrice = platforms.length > 0 ? Math.max(...platforms.filter(p => p.price).map(p => p.price!)) : null

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-[400px]">
                <div className="text-center">
                    <div className="spinner w-12 h-12 mx-auto mb-4"></div>
                    <p className="text-gray-600">로딩 중...</p>
                </div>
            </div>
        )
    }

    if (!detail) {
        return (
            <div className="modern-card p-12 text-center">
                <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <p className="text-gray-600 text-lg">차량 정보를 찾을 수 없습니다</p>
            </div>
        )
    }

    return (
        <div className="space-y-6 lg:space-y-8 animate-fade-in">
            {/* 상단 히어로 영역 */}
            <section className="modern-card p-6 lg:p-8">
                <div className="flex flex-col lg:flex-row gap-6 lg:gap-8">
                    <div className="w-full lg:w-[500px] h-[300px] lg:h-[400px] bg-gradient-to-br from-gray-100 to-gray-200 overflow-hidden rounded-2xl flex-shrink-0 shadow-inner">
                        <img
                            src={bigSrc}
                            alt={`${detail.specs?.maker || ''} ${detail.specs?.model || ''}`}
                            className="w-full h-full object-cover"
                            onError={()=> setBigSrc('/image/car/noimage/noimage.png')}
                        />
                    </div>
                    <div className="flex-1">
                        <h1 className="text-2xl lg:text-3xl font-bold mb-3">
                            {detail.specs?.maker || ''} {detail.specs?.model || ''} {detail.specs?.trim || ''}
                        </h1>
                        <div className="flex flex-wrap items-center gap-3 text-gray-600 mb-6">
                            {detail.specs?.year && (
                                <span className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 rounded-lg text-sm">
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                    </svg>
                                    {detail.specs.year}년식
                                </span>
                            )}
                            {detail.specs?.km && (
                                <span className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 rounded-lg text-sm">
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                    </svg>
                                    {detail.specs.km.toLocaleString()}km
                                </span>
                            )}
                            {detail.specs?.fuel && (
                                <span className="px-3 py-1.5 bg-gray-100 rounded-lg text-sm">{detail.specs.fuel}</span>
                            )}
                        </div>
                        {(minPrice || maxPrice) && (
                            <div className="bg-gradient-to-br from-blue-50 to-indigo-50 border-2 border-blue-200 rounded-xl p-4">
                                <p className="text-sm text-gray-600 mb-1">가격 범위</p>
                                <p className="text-2xl font-bold text-blue-600">
                                    {minPrice === maxPrice 
                                        ? `${minPrice?.toLocaleString()}원`
                                        : `${minPrice?.toLocaleString()} ~ ${maxPrice?.toLocaleString()}원`
                                    }
                                </p>
                            </div>
                        )}
                    </div>
                </div>
            </section>

            {/* 플랫폼별 링크 + 가격 비교 */}
            <section className="modern-card p-6 lg:p-8">
                <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                    </svg>
                    플랫폼별 매물 / 가격 비교
                </h2>
                <div className="overflow-x-auto">
                    <table className="min-w-full">
                        <thead>
                            <tr className="border-b-2 border-gray-200">
                                <th className="text-left p-3 text-sm font-semibold text-gray-700">선택</th>
                                <th className="text-left p-3 text-sm font-semibold text-gray-700">플랫폼</th>
                                <th className="text-left p-3 text-sm font-semibold text-gray-700">가격</th>
                                <th className="text-left p-3 text-sm font-semibold text-gray-700">상태</th>
                                <th className="text-left p-3 text-sm font-semibold text-gray-700">원문 링크</th>
                                <th className="text-left p-3 text-sm font-semibold text-gray-700">최근 관측</th>
                            </tr>
                        </thead>
                        <tbody>
                            {platforms.map((p, idx) => (
                                <tr 
                                    key={p.platformCarId} 
                                    className={`border-b border-gray-100 hover:bg-gray-50 transition ${idx % 2 === 0 ? 'bg-white' : 'bg-gray-50/50'}`}
                                >
                                    <td className="p-3">
                                        <input
                                            type="radio"
                                            name="pcid"
                                            onChange={()=>setPlatformCarId(p.platformCarId)}
                                            checked={platformCarId===p.platformCarId}
                                            className="w-4 h-4 text-black focus:ring-2 focus:ring-black"
                                        />
                                    </td>
                                    <td className="p-3">
                                        <span className="px-2 py-1 bg-gray-100 text-gray-700 rounded text-sm font-medium">
                                            {p.platform}
                                        </span>
                                    </td>
                                    <td className="p-3 font-bold text-lg text-blue-600">
                                        {p.price?.toLocaleString()}원
                                    </td>
                                    <td className="p-3">
                                        <span className={`px-2 py-1 rounded text-xs font-medium ${
                                            p.status === '판매중' ? 'bg-green-100 text-green-700' :
                                            p.status === '판매완료' ? 'bg-red-100 text-red-700' :
                                            'bg-gray-100 text-gray-700'
                                        }`}>
                                            {p.status}
                                        </span>
                                    </td>
                                    <td className="p-3">
                                        {p.pcUrl ? (
                                            <a 
                                                href={p.pcUrl} 
                                                target="_blank" 
                                                rel="noopener noreferrer"
                                                className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-700 font-medium transition"
                                            >
                                                바로가기
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                                </svg>
                                            </a>
                                        ) : (
                                            <span className="text-gray-400">-</span>
                                        )}
                                    </td>
                                    <td className="p-3 text-sm text-gray-600">{p.lastSeenDate || '-'}</td>
                                </tr>
                            ))}
                            {platforms.length===0 && (
                                <tr>
                                    <td className="p-6 text-center text-gray-500" colSpan={6}>
                                        플랫폼 데이터가 없습니다.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </section>

            {/* 가격 히스토리 */}
            {history.length > 0 && (
                <section className="modern-card p-6 lg:p-8">
                    <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                        </svg>
                        가격 히스토리
                    </h2>
                    <PriceChart points={history} />
                </section>
            )}

            {/* 매물 정보 (스펙) */}
            {detail && (
                <section className="modern-card p-6 lg:p-8">
                    <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        매물 정보
                    </h2>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <Spec label="제조사" value={detail.specs?.maker} />
                        <Spec label="모델" value={detail.specs?.model} />
                        <Spec label="트림" value={detail.specs?.trim} />
                        <Spec label="연식" value={detail.specs?.year?.toString()} />
                        <Spec label="주행거리" value={detail.specs?.km ? `${detail.specs.km.toLocaleString()} km` : undefined} />
                        <Spec label="연료" value={detail.specs?.fuel} />
                        <Spec label="변속기" value={detail.specs?.transmission} />
                        <Spec label="차종" value={detail.specs?.bodyType} />
                        <Spec label="지역" value={detail.specs?.region} />
                    </div>
                </section>
            )}
        </div>
    )
}

function Spec({label, value}:{label:string; value?:string}){
    return (
        <div className="flex items-center justify-between p-4 bg-gray-50 rounded-xl">
            <span className="text-sm text-gray-600 font-medium">{label}</span>
            <span className="font-bold text-gray-900 text-right">{value ?? '-'}</span>
        </div>
    )
}
