import React from 'react'
import { Link } from 'react-router-dom'

export default function Home(){
    return (
        <div className="space-y-6">
            <section className="bg-white border rounded-xl p-6 flex flex-col md:flex-row items-center gap-6">
                <img src="/image/logo/carizon_logo.png" alt="" className="w-24 h-auto" />
                <div className="flex-1">
                    <h1 className="text-2xl md:text-3xl font-bold">중고차 통합 검색 · 가격 비교</h1>
                    <p className="text-gray-600 mt-2">
                        엔카/차차차/KCar/차란차 등 주요 플랫폼의 매물을 한 번에 모아보고, 가격 변동과 이력을 확인하세요.
                    </p>
                    <div className="mt-4">
                        <Link to="/search" className="inline-flex items-center px-4 py-2 rounded-lg bg-black text-white">
                            지금 검색하기
                        </Link>
                    </div>
                </div>
            </section>

            <section className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-white border rounded-xl p-4">
                    <h3 className="font-semibold">인기 모델</h3>
                    <p className="text-sm text-gray-600 mt-1">빠른 바로가기: 제조사/모델 프리셋</p>
                    <div className="mt-3 flex flex-wrap gap-2">
                        <Link to="/search?makerCode=101" className="px-3 py-1 text-sm border rounded">현대</Link>
                        <Link to="/search?makerCode=102" className="px-3 py-1 text-sm border rounded">기아</Link>
                        <Link to="/search?makerCode=103" className="px-3 py-1 text-sm border rounded">BMW</Link>
                    </div>
                </div>
                <div className="bg-white border rounded-xl p-4">
                    <h3 className="font-semibold">최근 가격하락</h3>
                    <p className="text-sm text-gray-600 mt-1">검색에서 “최신순/낮은가격순”으로도 확인</p>
                    <div className="h-24 grid place-items-center text-gray-400 text-sm">샘플 슬롯</div>
                </div>
                <div className="bg-white border rounded-xl p-4">
                    <h3 className="font-semibold">광고 영역</h3>
                    <div className="h-24 grid place-items-center text-gray-400 text-sm border rounded">AD SLOT</div>
                </div>
            </section>
        </div>
    )
}
