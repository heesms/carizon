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
    fuel?: string
    region?: string
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
    const [totalElements, setTotalElements] = useState(0)
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
            setTotalElements(res?.totalElements ?? 0)
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
                        {!loading && (
                            <p className="text-sm text-gray-600 ml-13">
                                총 <span className="font-semibold text-gray-900">{totalElements.toLocaleString()}</span>개의 매물을 찾았습니다
                            </p>
                        )}
                    </div>
                    <div className="flex items-center gap-3">
                        {loading ? (
                            <div className="flex items-center gap-2 px-4 py-2 bg-gray-50 rounded-xl text-sm text-gray-600">
                                <div className="spinner w-4 h-4"></div>
                                <span>로딩 중…</span>
                            </div>
                        ) : (
                            <>
                                <span className="text-sm font-medium text-gray-600">정렬</span>
                                <select
                                    value={params.sort ?? ''}
                                    onChange={(e) => {
                                        const sort = e.target.value
                                        const next = new URLSearchParams(sp)
                                        if (sort) next.set('sort', sort); else next.delete('sort')
                                        next.set('page', '0')
                                        setSp(next)
                                        setPage(0)
                                        fetchPage(0)
                                    }}
                                    className="input-modern py-2 pl-3 pr-8 text-sm min-w-[140px]"
                                >
                                    <option value="">무작위</option>
                                    <option value="RECENT">최신순</option>
                                    <option value="LOW_PRICE">낮은 가격순</option>
                                    <option value="LOW_KM">적은 주행순</option>
                                    <option value="NEW_YEAR">신형순</option>
                                </select>
                            </>
                        )}
                    </div>
                </div>

                {/* 결과 리스트 (가격 정보 없는 매물은 표시하지 않음) */}
                <div className="grid gap-4">
                    {list
                        .filter((it) => it && (it.priceMin != null || it.priceMax != null))
                        .map((it, idx) => (
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

                {totalPages > 1 && (() => {
                    const maxVisible = 10
                    const half = Math.floor(maxVisible / 2)
                    let startPage = Math.max(0, page - half)
                    let endPage = Math.min(totalPages - 1, startPage + maxVisible - 1)
                    if (endPage - startPage + 1 < maxVisible && startPage > 0) {
                        startPage = Math.max(0, endPage - maxVisible + 1)
                    }
                    const visiblePages = Array.from({ length: endPage - startPage + 1 }, (_, i) => startPage + i)
                    return (
                        <div className="flex items-center justify-center gap-1 sm:gap-2 flex-wrap pt-4">
                            <button
                                type="button"
                                onClick={() => {
                                    const next = Math.max(0, page - 1)
                                    setSp(prev => { prev.set('page', String(next)); return prev })
                                    fetchPage(next)
                                }}
                                disabled={page === 0}
                                className="p-2 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                                aria-label="이전 페이지"
                            >
                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
                            </button>
                            {visiblePages.map((p) => (
                                <button
                                    key={p}
                                    type="button"
                                    onClick={() => {
                                        setSp(prev => { prev.set('page', String(p)); return prev })
                                        fetchPage(p)
                                    }}
                                    className={`min-w-[2.25rem] px-3 py-2 rounded-lg font-medium transition-all ${
                                        p === page
                                            ? 'bg-black text-white shadow-lg scale-105'
                                            : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 hover:border-gray-300'
                                    }`}
                                >
                                    {p + 1}
                                </button>
                            ))}
                            <button
                                type="button"
                                onClick={() => {
                                    const next = Math.min(totalPages - 1, page + 1)
                                    setSp(prev => { prev.set('page', String(next)); return prev })
                                    fetchPage(next)
                                }}
                                disabled={page >= totalPages - 1}
                                className="p-2 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                                aria-label="다음 페이지"
                            >
                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" /></svg>
                            </button>
                        </div>
                    )
                })()}
            </section>
        </div>
    )
}
