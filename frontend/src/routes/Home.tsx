import React from 'react'
import { Link } from 'react-router-dom'
import Recommendation from '@/routes/Recommendation'
import SeoMeta from '@/components/SeoMeta'

export default function Home() {
    return (
        <>
            <SeoMeta
                title="중고차 통합검색·가격비교 - Carizon | AI 추천으로 빠르게 찾기"
                description="Carizon은 여러 플랫폼의 중고차 매물을 한곳에서 통합 검색하고 가격·연식·주행거리 조건으로 비교할 수 있는 서비스입니다. AI 추천으로 원하는 차량을 빠르게 찾으세요."
                keywords="중고차, 중고차 검색, 중고차 매물, 중고차 추천, Carizon, 제조사별 중고차, 가격비교"
                canonicalPath="/"
                ogImage="/favicon.png"
            />

            <div className="animate-fade-in grid grid-cols-1 lg:grid-cols-2 lg:grid-rows-3 gap-6 lg:gap-8">
            {/* 왼쪽 절반 · 세로 3칸: AI 차량 추천 (입력 유지, 추천받기 누르면 AI 추천 페이지로 이동) */}
            <div className="lg:row-span-3 min-h-0 flex flex-col">
                <Recommendation simplified />
            </div>

            {/* 오른쪽 절반 · 세로 1칸씩: 검색, 광고, 최근가격하락 */}
            <FeatureCard
                title="검색"
                    description="제조사·모델로 바로 검색"
                    icon={
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                    }
                >
                    <div className="mt-4 flex flex-wrap gap-2">
                        <Link
                            to="/search"
                            className="px-4 py-2 text-sm font-medium bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition inline-flex items-center gap-2"
                        >
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                            </svg>
                            지금 검색하기
                        </Link>
                        <Link
                            to="/search?makerCode=101"
                            className="px-4 py-2 text-sm font-medium bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition"
                        >
                            현대
                        </Link>
                        <Link
                            to="/search?makerCode=102"
                            className="px-4 py-2 text-sm font-medium bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition"
                        >
                            기아
                        </Link>
                        <Link
                            to="/search?makerCode=103"
                            className="px-4 py-2 text-sm font-medium bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition"
                        >
                            BMW
                        </Link>
                    </div>
                </FeatureCard>

            <FeatureCard
                title="광고 영역"
                description="프리미엄 광고 배너"
                icon={
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
                    </svg>
                }
            >
                <div className="mt-4 h-32 grid place-items-center text-gray-400 text-sm border-2 border-dashed border-gray-200 rounded-xl bg-gradient-to-br from-gray-50 to-gray-100">
                    <div className="text-center">
                        <p className="text-xs font-medium">AD SLOT</p>
                    </div>
                </div>
            </FeatureCard>

            <FeatureCard
                title="최근 가격하락"
                    description="검색에서 '최신순/낮은가격순'으로 확인"
                    icon={
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6" />
                        </svg>
                    }
                >
                    <div className="mt-4">
                        <Link
                            to="/search"
                            className="text-sm font-medium text-blue-600 hover:text-blue-700 hover:underline"
                        >
                            검색 페이지로 이동 →
                        </Link>
                    </div>
                </FeatureCard>
            </div>
        </>
    )
}

function FeatureCard({
    title,
    description,
    icon,
    children,
}: {
    title: string
    description: string
    icon: React.ReactNode
    children: React.ReactNode
}) {
    return (
        <div className="modern-card p-6 hover-lift">
            <div className="flex items-start gap-4">
                <div className="flex-shrink-0 w-12 h-12 bg-gradient-to-br from-black to-gray-700 rounded-xl flex items-center justify-center text-white">
                    {icon}
                </div>
                <div className="flex-1 min-w-0">
                    <h3 className="font-bold text-lg mb-1">{title}</h3>
                    <p className="text-sm text-gray-600">{description}</p>
                    {children}
                </div>
            </div>
        </div>
    )
}
