import React, { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

// 상대경로로 고정 (별칭 @ 안 씀)
import FiltersPanel from '../components/FiltersPanel'
import CarCard from '../components/CarCard'
// API 타입/함수 (경로는 실제 위치에 맞게 조정: services가 src 바로 아래면 ../services/api)
import { searchCars } from '../services/api'

// 리스트 항목 타입 – 불확실하면 여기서 다시 선언(프론트만 씀)
export type CarListItem = {
    carId: number
    maker: string
    model: string
    trim?: string
    year?: number
    km?: number
    priceMin?: number
    priceMax?: number
    priceUpdatedAt?: string
    representativeImageUrl?: string
    modelCode?: string
}
// 페이지 응답 타입(백엔드 표준 페이징)
export type CarListResponse = {
    content: CarListItem[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export default function Search() {
    const [sp, setSp] = useSearchParams()
    const [list, setList] = useState<CarListItem[]>([])
    const [page, setPage] = useState(0)
    const [totalPages, setTotalPages] = useState(0)
    const [loading, setLoading] = useState(false)
    const params = useMemo(() => Object.fromEntries(sp.entries()), [sp])

    const fetchPage = async (p = 0) => {
        try {
            setLoading(true)
            // 최초/이동 모두 20개씩 고정
            const response = await searchCars({ ...params, page: p, size: 20 })
            // 백엔드 응답 구조: { success: true, data: { content: [...], page, size, ... } }
            const res: CarListResponse = (response as any).data || response
            setList((res && res.content) ? res.content : [])
            setTotalPages(res?.totalPages ?? 0)
        } catch (e) {
            setList([])
            setTotalPages(0)
            console.error(e)
        } finally {
            setLoading(false)
        }
    }

    // 첫 진입 자동 조회
    useEffect(() => { fetchPage(0) }, [])
    // URL의 page 동기화
    useEffect(() => { setPage(Number(params.page || 0)) }, [params.page])

    // 필터 변경 → URL 업데이트
    const setFilters = (v: Record<string, any>) => {
        const usp = new URLSearchParams()
        Object.entries(v).forEach(([k, val]) => {
            if (val !== undefined && val !== null && val !== '') usp.set(k, String(val))
        })
        usp.set('page', '0')
        setSp(usp)
    }

    return (
        <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-6 animate-fade-in">
            <FiltersPanel value={params} onChange={setFilters} onSearch={() => fetchPage(0)} />

            <section className="space-y-6">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div>
                        <div className="flex items-center gap-3 mb-2">
                            <div className="w-10 h-10 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-xl flex items-center justify-center">
                                <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                </svg>
                            </div>
                            <h2 className="text-3xl font-bold">검색 결과</h2>
                        </div>
                        {!loading && list.length > 0 && (
                            <p className="text-sm text-gray-600 ml-13">
                                총 <span className="font-semibold text-gray-900">{list.length}</span>개의 매물을 찾았습니다
                            </p>
                        )}
                    </div>
                    {loading && (
                        <div className="flex items-center gap-2 px-4 py-2 bg-gray-50 rounded-xl text-sm text-gray-600">
                            <div className="spinner w-4 h-4"></div>
                            <span>로딩 중…</span>
                        </div>
                    )}
                </div>

                {/* 결과 리스트 */}
                <div className="grid gap-4">
                    {list.filter(Boolean).map((it, idx) => (
                        <div key={it.carId} className="animate-fade-in" style={{ animationDelay: `${idx * 0.05}s` }}>
                            <CarCard item={it} compact />
                        </div>
                    ))}
                </div>

                {!loading && list.length === 0 && (
                    <div className="modern-card p-12 text-center">
                        <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <p className="text-gray-600 text-lg mb-2">조건에 맞는 결과가 없습니다</p>
                        <p className="text-sm text-gray-500">필터를 조정해 보세요</p>
                    </div>
                )}

                {totalPages > 1 && (
                    <div className="flex items-center justify-center gap-2 flex-wrap pt-4">
                        {Array.from({ length: totalPages }, (_, i) => i).map((p) => (
                            <button
                                key={p}
                                onClick={() => {
                                    setSp(prev => { prev.set('page', String(p)); return prev })
                                    fetchPage(p)
                                }}
                                className={`px-4 py-2 rounded-lg font-medium transition-all ${
                                    p === page
                                        ? 'bg-black text-white shadow-lg scale-105'
                                        : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 hover:border-gray-300'
                                }`}
                            >
                                {p + 1}
                            </button>
                        ))}
                    </div>
                )}
            </section>
        </div>
    )
}
