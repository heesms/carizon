import React from 'react'
import { Link } from 'react-router-dom'

export default function Home(){
    return (
        <div className="space-y-8 lg:space-y-12 animate-fade-in">
            {/* 히어로 섹션 */}
            <section className="relative overflow-hidden bg-gradient-to-br from-black via-gray-900 to-black rounded-2xl lg:rounded-3xl p-8 lg:p-12 text-white">
                <div className="absolute inset-0 bg-[url('/image/logo/carizon_logo.png')] bg-cover bg-center opacity-5"></div>
                <div className="relative z-10 flex flex-col lg:flex-row items-center gap-8">
                    <div className="flex-shrink-0">
                        <img 
                            src="/image/logo/carizon_logo.png" 
                            alt="Carizon Logo" 
                            className="w-32 h-32 lg:w-40 lg:h-40 object-contain drop-shadow-2xl" 
                        />
                    </div>
                    <div className="flex-1 text-center lg:text-left">
                        <h1 className="text-3xl lg:text-5xl font-bold mb-4 leading-tight">
                            중고차 통합 검색
                            <br />
                            <span className="bg-gradient-to-r from-blue-400 to-purple-400 bg-clip-text text-transparent">
                                AI 가격 비교
                            </span>
                        </h1>
                        <p className="text-gray-300 text-lg lg:text-xl mb-6 max-w-2xl mx-auto lg:mx-0">
                            엔카, 차차차, KCar, 차란차 등 주요 플랫폼의 매물을 한 번에 모아보고, 
                            가격 변동과 이력을 확인하세요.
                        </p>
                        <div className="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start">
                            <Link 
                                to="/search" 
                                className="px-6 py-3 bg-white text-black rounded-xl font-semibold inline-flex items-center justify-center gap-2 hover:bg-gray-100 transition-all duration-200 shadow-lg hover:shadow-xl transform hover:-translate-y-0.5"
                            >
                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                </svg>
                                지금 검색하기
                            </Link>
                            <Link 
                                to="/recommendation" 
                                className="px-6 py-3 bg-white/10 backdrop-blur-sm border-2 border-white/30 text-white rounded-xl font-semibold inline-flex items-center justify-center gap-2 hover:bg-white/20 hover:border-white/50 transition-all duration-200"
                            >
                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                                </svg>
                                AI 추천받기
                            </Link>
                        </div>
                    </div>
                </div>
            </section>

            {/* 기능 카드 섹션 */}
            <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                <FeatureCard
                    title="인기 모델"
                    description="빠른 바로가기: 제조사/모델 프리셋"
                    icon={
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                        </svg>
                    }
                >
                    <div className="mt-4 flex flex-wrap gap-2">
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
                    title="최근 가격하락"
                    description="검색에서 '최신순/낮은가격순'으로도 확인"
                    icon={
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6" />
                        </svg>
                    }
                >
                    <div className="mt-4 h-32 grid place-items-center text-gray-400 text-sm border-2 border-dashed border-gray-200 rounded-xl">
                        <div className="text-center">
                            <p className="text-xs">샘플 데이터</p>
                        </div>
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
            </section>
        </div>
    )
}

function FeatureCard({ 
    title, 
    description, 
    icon, 
    children 
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
