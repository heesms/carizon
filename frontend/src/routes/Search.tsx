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
            const res: CarListResponse = await searchCars({ ...params, page: p, size: 20 })
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
        <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-4">
            <FiltersPanel value={params} onChange={setFilters} onSearch={() => fetchPage(0)} />

            <section className="space-y-3">
                <div className="flex items-center justify-between">
                    <h2 className="font-semibold">검색 결과</h2>
                    {loading && <span className="text-sm text-gray-500">로딩 중…</span>}
                </div>

                {/* 결과 리스트: null/undefined 방어 */}
                <div className="grid gap-3">
                    {list.filter(Boolean).map((it) => (
                        <CarCard key={it.carId} item={it} compact />
                    ))}
                </div>

                {!loading && list.length === 0 && (
                    <div className="text-sm text-gray-500">조건에 맞는 결과가 없습니다. 필터를 조정해 보세요.</div>
                )}

                {totalPages > 1 && (
                    <div className="flex gap-1 flex-wrap">
                        {Array.from({ length: totalPages }, (_, i) => i).map((p) => (
                            <button
                                key={p}
                                onClick={() => {
                                    setSp(prev => { prev.set('page', String(p)); return prev })
                                    fetchPage(p)
                                }}
                                className={'px-3 py-1 rounded border ' + (p === page ? 'bg-black text-white' : 'bg-white')}
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
