import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getCarDetail, type CarDetail as CarDetailType } from '@/services/api'
import SeoMeta from '@/components/SeoMeta'
import { getCachedImageSource } from '@/utils/imageCache'

const MOBILE_MEDIA = '(max-width: 767px)'
function useIsMobile() {
    const [isMobile, setIsMobile] = useState(false)
    useEffect(() => {
        if (typeof window === 'undefined') return
        const mq = window.matchMedia(MOBILE_MEDIA)
        const update = () => setIsMobile(mq.matches)
        update()
        mq.addEventListener('change', update)
        return () => mq.removeEventListener('change', update)
    }, [])
    return isMobile
}

function bigImageUrl(modelCode?: string, fallback?: string){
    if (modelCode) return `/image/car/model/${modelCode}.webp`
    return fallback || '/image/car/noimage/noimage.png'
}

/** 플랫폼 영문명 → 한글 표기 */
function platformDisplayName(platform: string): string {
    if (!platform) return platform
    const lower = platform.toLowerCase()
    const map: Record<string, string> = {
        chachacha: '차차차',
        encar: '엔카',
        encar_truck: '엔카',
        'encar-truck': '엔카',
        chutcha: '첫차',
        kcar: 'K캐어',
        tcar: '티카',
        charancha: '차란차',
    }
    return map[lower] || platform
}

export default function CarDetail(){
    const { id } = useParams()
    const isMobile = useIsMobile()
    const [detail, setDetail] = useState<CarDetailType | null>(null)
    const [bigSrc, setBigSrc] = useState<string>('/image/car/noimage/noimage.png')
    const [loading, setLoading] = useState(true)
    const carTitle = detail
        ? `${detail.specs?.maker || ''} ${detail.specs?.model || ''} ${detail.specs?.trim || ''} 중고차 매물 상세`
        : '중고차 매물 상세 페이지'
    const carDescription = detail
        ? `${detail.specs?.maker || ''} ${detail.specs?.model || ''} ${detail.specs?.trim || ''}의 연식, 주행거리, 연료, 지역, 변속기 정보를 포함한 차량 가격 비교 페이지입니다.`
        : '중고차 상세 스펙, 연식, 주행거리, 연료 정보와 플랫폼별 가격을 비교할 수 있습니다.'
    const carCanonicalPath = `/cars/${id || ''}`

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
            const fallback = (rawData.representativeImageUrl as string) || undefined
            const preferred = bigImageUrl(mc, fallback)
            setBigSrc(preferred)
            getCachedImageSource(preferred)
                .then((cached) => setBigSrc(cached || preferred))
                .catch(() => setBigSrc(preferred))
        }).catch((err) => {
            console.error('Failed to load car detail:', err)
            setDetail(null)
        })
        .finally(() => setLoading(false))
    },[id])

    const platforms = detail?.platforms || []
    const minPrice = platforms.length > 0 ? Math.min(...platforms.filter(p => p.price).map(p => p.price!)) : null
    const maxPrice = platforms.length > 0 ? Math.max(...platforms.filter(p => p.price).map(p => p.price!)) : null

    if (loading) {
        return (
            <>
                <SeoMeta
                    title={carTitle}
                    description={carDescription}
                    canonicalPath={carCanonicalPath}
                    keywords="중고차 상세, 차량 스펙, 가격 비교, 플랫폼별 가격"
                    ogImage="/favicon.png"
                />

                <div className="flex items-center justify-center min-h-[400px]">
                    <div className="text-center">
                        <div className="spinner w-12 h-12 mx-auto mb-4"></div>
                        <p className="text-gray-600">로딩 중...</p>
                    </div>
                </div>
            </>
        )
    }

    if (!detail) {
        return (
            <>
                <SeoMeta
                    title={carTitle}
                    description={carDescription}
                    canonicalPath={carCanonicalPath}
                    noindex
                    keywords="중고차 상세, 차량 스펙, 가격 비교, 차량 플랫폼 비교"
                    ogImage="/favicon.png"
                />
                <div className="modern-card p-12 text-center">
                    <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p className="text-gray-600 text-lg">차량 정보를 찾을 수 없습니다</p>
                </div>
            </>
        )
    }

    return (
        <>
            <SeoMeta
                title={carTitle}
                description={carDescription}
                canonicalPath={carCanonicalPath}
                keywords="중고차 상세, 차량 스펙, 가격 비교, 플랫폼별 가격"
                ogImage="/favicon.png"
            />

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
                                        ? `${minPrice?.toLocaleString()}만원`
                                        : `${minPrice?.toLocaleString()} ~ ${maxPrice?.toLocaleString()}만원`
                                    }
                                </p>
                            </div>
                        )}

                        {/* 플랫폼별 가격 비교 그래프 (가격 범위 바로 아래) */}
                        {platforms.filter(p => p.price != null).length > 0 && (() => {
                            const maxVal = maxPrice || 1
                            return (
                                <div className="mt-6">
                                    <p className="text-sm font-semibold text-gray-700 mb-3">플랫폼별 매물 가격 비교</p>
                                    <div className="space-y-3">
                                        {platforms
                                            .filter(p => p.price != null)
                                            .sort((a, b) => (a.price ?? 0) - (b.price ?? 0))
                                            .map((p) => {
                                                const platformUrl = p.pcUrl || p.mUrl || null
                                                const label = platformDisplayName(p.platform)
                                                return (
                                                    <div key={p.platformCarId} className="flex items-center gap-3 flex-wrap">
                                                        <div className="w-28 shrink-0 flex items-center gap-1.5">
                                                            {platformUrl ? (
                                                                <a
                                                                    href={platformUrl}
                                                                    target="_blank"
                                                                    rel="noopener noreferrer"
                                                                    className="text-sm font-medium text-blue-600 hover:text-blue-700 hover:underline inline-flex items-center gap-1"
                                                                    title="해당 플랫폼에서 보기"
                                                                >
                                                                    {label}
                                                                    <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                                                    </svg>
                                                                </a>
                                                            ) : (
                                                                <span className="text-sm font-medium text-gray-700">{label}</span>
                                                            )}
                                                        </div>
                                                        <div className="flex-1 h-8 bg-gray-100 rounded-lg overflow-hidden min-w-0">
                                                            <div
                                                                className="h-full bg-gradient-to-r from-blue-500 to-indigo-500 rounded-lg transition-all duration-500 min-w-[2rem] flex items-center justify-end pr-2"
                                                                style={{ width: `${Math.max(8, ((p.price ?? 0) / maxVal) * 100)}%` }}
                                                            >
                                                                <span className="text-xs font-bold text-white drop-shadow">
                                                                    {(p.price ?? 0).toLocaleString()}만원
                                                                </span>
                                                            </div>
                                                        </div>
                                                    </div>
                                                )
                                            })}
                                    </div>
                                </div>
                            )
                        })()}
                    </div>
                </div>
            </section>

            {/* 매물 정보 (스펙) - 플랫폼에서 보기보다 위로 */}
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

            {/* 매물 정보를 플랫폼에서 보기 (버튼 강조) */}
            <section className="modern-card p-6 lg:p-8">
                <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
                    </svg>
                    매물 정보를 플랫폼에서 보기
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {platforms.map((p) => (
                        <div
                            key={p.platformCarId}
                            className="flex flex-col sm:flex-row sm:items-center gap-4 p-4 rounded-xl border-2 border-gray-100 hover:border-blue-200 hover:bg-blue-50/50 transition-all"
                        >
                            <div className="flex-1 min-w-0">
                                <span className="font-semibold text-gray-800">{platformDisplayName(p.platform)}</span>
                                {p.price != null && (
                                    <p className="text-lg font-bold text-blue-600 mt-0.5">{p.price.toLocaleString()}만원</p>
                                )}
                            </div>
                            {(p.pcUrl || p.mUrl) ? (
                                <a
                                    href={isMobile ? (p.mUrl || p.pcUrl || '#') : (p.pcUrl || p.mUrl || '#')}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="inline-flex items-center justify-center gap-2 px-5 py-3 bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-semibold rounded-xl shadow-md hover:from-blue-700 hover:to-indigo-700 hover:shadow-lg transition-all shrink-0"
                                >
                                    {platformDisplayName(p.platform)}에서 보기
                                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                                    </svg>
                                </a>
                            ) : (
                                <span className="text-gray-400 text-sm shrink-0">링크 없음</span>
                            )}
                        </div>
                    ))}
                    {platforms.length === 0 && (
                        <p className="col-span-full text-center text-gray-500 py-6">플랫폼 데이터가 없습니다.</p>
                    )}
                </div>
            </section>
        </div>
        </>
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
