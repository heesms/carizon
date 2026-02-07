import React, { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { getRecommendations, extractImageFromUrl, type RecommendationResponse, type RecommendedCar } from '@/api/recommendations'
import { getLikeInfo, toggleLike, type LikeInfo } from '@/api/likes'

export default function Recommendation() {
    const [query, setQuery] = useState('')
    const [maxPrice, setMaxPrice] = useState<string>('')
    const [minPrice, setMinPrice] = useState<string>('')
    const [loading, setLoading] = useState(false)
    const [result, setResult] = useState<RecommendationResponse | null>(null)
    const [error, setError] = useState<string | null>(null)
    const resultRef = useRef<HTMLDivElement>(null)

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!query.trim()) {
            setError('요구사항을 입력해주세요.')
            return
        }

        setLoading(true)
        setError(null)
        setResult(null)

        try {
            const response = await getRecommendations({
                query: query.trim(),
                maxResults: 5,
                minPrice: minPrice ? parseInt(minPrice) : undefined,
                maxPrice: maxPrice ? parseInt(maxPrice) : undefined,
            })
            setResult(response)
            
            // 결과가 나오면 스크롤 다운
            setTimeout(() => {
                if (resultRef.current) {
                    resultRef.current.scrollIntoView({ 
                        behavior: 'smooth', 
                        block: 'start' 
                    })
                }
            }, 100)
        } catch (err) {
            setError(err instanceof Error ? err.message : '추천 요청 중 오류가 발생했습니다.')
        } finally {
            setLoading(false)
        }
    }

    const allExampleQueries = [
        '가족용 SUV, 3000만원 이하',
        '연비 좋은 경차 추천',
        '전기차 5000만원 이하',
        '고급 세단 추천',
        '중형 SUV 추천해줘',
        '주행거리 적은 중고차',
        '2020년 이후 출시된 차량',
        '하이브리드 차량 추천',
        '7인승 가족용 차량',
        '경차나 소형차 추천',
        '디젤 엔진 차량',
        '가성비 좋은 중고차',
        '프리미엄 브랜드 추천',
        '실용적인 차량 찾아줘',
        '스포츠카 추천',
        '럭셔리 세단 찾아줘',
        '경차 추천',
        '대형 SUV 추천',
        '수입차 추천',
        '국산차 추천',
        '전기차 추천',
        '하이브리드 추천',
        '가족용 차량 추천',
        '출퇴근용 차량',
        '장거리 운전용 차량',
        '도심 주행용 차량',
        '주차하기 쉬운 소형차',
        '넓은 실내 공간 차량',
        '연비 좋은 차량',
        '안전 기능 많은 차량',
        '스마트 기능 많은 차량',
        '디자인 예쁜 차량',
        '실내가 넓은 차량',
        '트렁크 넓은 차량',
        '오프로드 주행 가능 차량',
        '고속도로 주행 좋은 차량',
        '도심 연비 좋은 차량',
        '저렴한 중고차',
        '신차 같은 중고차',
        '주행거리 5만km 이하',
        '주행거리 10만km 이하',
        '2021년 이후 차량',
        '2022년 이후 차량',
        '2023년 이후 차량',
        '최신형 차량',
        '인기 차량 추천',
        '보험료 저렴한 차량',
        '유지비 저렴한 차량',
        '수리비 저렴한 차량',
        '부품 구하기 쉬운 차량',
        '중고차 시세 안정적인 차량',
        '재판매 가치 높은 차량',
        '검은색 차량',
        '흰색 차량',
        '은색 차량',
        '빨간색 차량',
        '파란색 차량',
        '회색 차량',
        '자동변속기 차량',
        '수동변속기 차량',
        'CVT 변속기 차량',
        'DCT 변속기 차량',
        '가솔린 차량',
        'LPG 차량',
        'CNG 차량',
        '수소차 추천',
        '플러그인 하이브리드',
        '마일드 하이브리드',
        '풀 하이브리드',
        '현대차 추천',
        '기아차 추천',
        '제네시스 추천',
        '쉐보레 추천',
        '르노삼성 추천',
        'GM 추천',
        'BMW 추천',
        '벤츠 추천',
        '아우디 추천',
        '폭스바겐 추천',
        '볼보 추천',
        '렉서스 추천',
        '인피니티 추천',
        '혼다 추천',
        '도요타 추천',
        '닛산 추천',
        '마쓰다 추천',
        '미쓰비시 추천',
        '스바루 추천',
        '캐딜락 추천',
        '링컨 추천',
        '포드 추천',
        '지프 추천',
        '랜드로버 추천',
        '재규어 추천',
        '미니 추천',
        '피아트 추천',
        '시트로엥 추천',
        '푸조 추천',
        '르노 추천',
        '스마트 추천',
        '테슬라 추천',
        '폴스타 추천',
        'BYD 추천',
        '폴링 추천',
        '1000만원 이하 차량',
        '2000만원 이하 차량',
        '4000만원 이하 차량',
        '6000만원 이하 차량',
        '8000만원 이하 차량',
        '1억원 이하 차량',
        '소형 SUV',
        '중대형 SUV',
        '풀사이즈 SUV',
        '컴팩트 SUV',
        '미드사이즈 SUV',
        '컴팩트 세단',
        '미드사이즈 세단',
        '풀사이즈 세단',
        '스포츠 세단',
        '쿠페',
        '컨버터블',
        '왜건',
        '해치백',
        '세단형 해치백',
        '픽업트럭',
        '밴',
        '미니밴',
        '캠핑카',
        '리무진',
        '스포츠카',
        '슈퍼카',
        '하이퍼카',
        '클래식카',
        '빈티지카',
        '레트로카',
        '컨셉카',
        '튜닝카',
        '레이싱카',
        '오프로드 차량',
        '사막 주행용',
        '눈길 주행용',
        '산길 주행용',
        '고속도로 주행용',
        '도심 주행용',
        '장거리 여행용',
        '단거리 출퇴근용',
        '배달용 차량',
        '택시용 차량',
        '렌터카용 차량',
        '리스용 차량',
        '리스폰스빌리티 차량',
        '리스폰스빌리티 없는 차량',
        '사고 이력 없는 차량',
        '수리 이력 적은 차량',
        '정비 이력 깨끗한 차량',
        '원주인 차량',
        '여성 원주인 차량',
        '남성 원주인 차량',
        '노인 원주인 차량',
        '청년 원주인 차량',
        '중년 원주인 차량',
        '서울 지역 차량',
        '경기 지역 차량',
        '인천 지역 차량',
        '부산 지역 차량',
        '대구 지역 차량',
        '광주 지역 차량',
        '대전 지역 차량',
        '울산 지역 차량',
        '강원 지역 차량',
        '충청 지역 차량',
        '전라 지역 차량',
        '경상 지역 차량',
        '제주 지역 차량',
        '수도권 차량',
        '지방 차량',
        '해외 수입 차량',
        '국내 생산 차량',
        '완전 수입 차량',
        '반조립 수입 차량',
        'KD 조립 차량',
        '완전 국산 차량',
        '수입차 브랜드',
        '국산차 브랜드',
        '유럽차 브랜드',
        '일본차 브랜드',
        '미국차 브랜드',
        '중국차 브랜드',
        '한국차 브랜드',
        '독일차 브랜드',
        '영국차 브랜드',
        '이탈리아차 브랜드',
        '프랑스차 브랜드',
        '스웨덴차 브랜드',
    ]
    
    // 새로고침 시 랜덤 시작 인덱스 (한 번만 생성, 세션 동안 유지)
    const [startIndex, setStartIndex] = useState(() => {
        const saved = sessionStorage.getItem('exampleStartIndex')
        if (saved) return parseInt(saved)
        const random = Math.floor(Math.random() * allExampleQueries.length)
        sessionStorage.setItem('exampleStartIndex', random.toString())
        return random
    })
    
    const [currentOffset, setCurrentOffset] = useState(0)
    const examplesPerPage = 4
    const totalPages = Math.ceil(allExampleQueries.length / examplesPerPage)
    
    // 현재 페이지의 예시들
    const displayedExamples = React.useMemo(() => {
        const examples = []
        for (let i = 0; i < examplesPerPage; i++) {
            const idx = (startIndex + currentOffset + i) % allExampleQueries.length
            examples.push(allExampleQueries[idx])
        }
        return examples
    }, [startIndex, currentOffset])
    
    // 현재 페이지 번호 계산 (0부터 시작)
    const currentPageNumber = React.useMemo(() => {
        const totalOffset = startIndex + currentOffset
        return Math.floor(totalOffset / examplesPerPage) % totalPages
    }, [startIndex, currentOffset, totalPages])
    
    const handlePrevPage = () => {
        setCurrentOffset((prev) => {
            const newOffset = prev - examplesPerPage
            // 순환: 음수가 되면 마지막 페이지로
            if (newOffset < 0) {
                return (totalPages - 1) * examplesPerPage
            }
            return newOffset
        })
    }
    
    const handleNextPage = () => {
        setCurrentOffset((prev) => {
            const newOffset = prev + examplesPerPage
            // 순환: 전체를 넘어가면 처음으로
            if (newOffset >= allExampleQueries.length) {
                return 0
            }
            return newOffset
        })
    }

    return (
        <div className="space-y-8 animate-fade-in">
            {/* 헤더 섹션 */}
            <section className="relative overflow-hidden bg-gradient-to-br from-purple-600 via-blue-600 to-indigo-700 rounded-2xl lg:rounded-3xl p-8 lg:p-12 text-white">
                <div className="absolute inset-0 bg-black/10"></div>
                <div className="relative z-10">
                    <div className="flex items-center gap-3 mb-4">
                        <div className="w-12 h-12 bg-white/20 backdrop-blur-sm rounded-xl flex items-center justify-center">
                            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                            </svg>
                        </div>
                        <h1 className="text-3xl lg:text-4xl font-bold">AI 차량 추천</h1>
                    </div>
                    <p className="text-lg text-white/90 max-w-2xl">
                        원하는 차량의 조건을 자연어로 입력하면, AI가 최적의 차량을 추천해드립니다.
                    </p>
                </div>
            </section>

            {/* 입력 폼 */}
            <section className="modern-card p-6 lg:p-8">
                <form onSubmit={handleSubmit} className="space-y-6">
                    <div>
                        <label htmlFor="query" className="block text-sm font-semibold text-gray-700 mb-3">
                            원하는 차량 조건을 입력하세요
                        </label>
                        <textarea
                            id="query"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="예: 가족용 SUV, 3000만원 이하, 연비 좋은 차량"
                            className="input-modern min-h-[120px] resize-none"
                            rows={4}
                            disabled={loading}
                        />
                        <div className="mt-3">
                            <div className="mt-3">
                                <p className="text-xs text-gray-500 mb-2">예시:</p>
                                <div className="flex flex-wrap gap-2">
                                    {displayedExamples.map((example, idx) => (
                                        <button
                                            key={`${example}-${currentPageNumber}-${idx}`}
                                            type="button"
                                            onClick={() => setQuery(example)}
                                            className="px-3 py-1.5 text-xs bg-gray-100 hover:bg-gray-200 hover:scale-105 text-gray-700 rounded-lg transition-all duration-200 font-medium"
                                            disabled={loading}
                                        >
                                            {example}
                                        </button>
                                    ))}
                                </div>
                                <div className="flex items-center justify-center gap-2 mt-2">
                                    <button
                                        type="button"
                                        onClick={handlePrevPage}
                                        disabled={loading}
                                        className="w-6 h-6 flex items-center justify-center rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition"
                                        aria-label="이전 예시"
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                                        </svg>
                                    </button>
                                    <span className="text-xs text-gray-400 min-w-[40px] text-center">
                                        {currentPageNumber + 1} / {totalPages}
                                    </span>
                                    <button
                                        type="button"
                                        onClick={handleNextPage}
                                        disabled={loading}
                                        className="w-6 h-6 flex items-center justify-center rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed transition"
                                        aria-label="다음 예시"
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                        </svg>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label htmlFor="minPrice" className="block text-sm font-semibold text-gray-700 mb-2">
                                최소 가격 (만원)
                            </label>
                            <input
                                id="minPrice"
                                type="number"
                                value={minPrice}
                                onChange={(e) => setMinPrice(e.target.value)}
                                placeholder="예: 1000"
                                className="input-modern"
                                disabled={loading}
                            />
                        </div>
                        <div>
                            <label htmlFor="maxPrice" className="block text-sm font-semibold text-gray-700 mb-2">
                                최대 가격 (만원)
                            </label>
                            <input
                                id="maxPrice"
                                type="number"
                                value={maxPrice}
                                onChange={(e) => setMaxPrice(e.target.value)}
                                placeholder="예: 5000"
                                className="input-modern"
                                disabled={loading}
                            />
                        </div>
                    </div>

                    <button
                        type="submit"
                        disabled={loading || !query.trim()}
                        className="btn-primary w-full flex items-center justify-center gap-2"
                    >
                        {loading ? (
                            <>
                                <div className="spinner w-5 h-5"></div>
                                <span>추천 중...</span>
                            </>
                        ) : (
                            <>
                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                </svg>
                                <span>차량 추천받기</span>
                            </>
                        )}
                    </button>
                </form>
            </section>

            {/* 에러 메시지 */}
            {error && (
                <section className="bg-red-50 border-2 border-red-200 rounded-xl p-6 animate-fade-in">
                    <div className="flex items-start gap-3">
                        <svg className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <div>
                            <h3 className="font-semibold text-red-800 mb-1">오류가 발생했습니다</h3>
                            <p className="text-red-700 text-sm">{error}</p>
                        </div>
                    </div>
                </section>
            )}

            {/* 추천 결과 */}
            {result && (
                <div ref={resultRef} className="space-y-6 animate-fade-in">
                    {/* LLM 추천 설명 - 트렌디한 디자인 */}
                    {result.recommendation && (
                        <section className="relative overflow-hidden bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 rounded-2xl lg:rounded-3xl p-6 lg:p-8 text-white shadow-2xl">
                            {/* 배경 패턴 */}
                            <div className="absolute inset-0 opacity-10">
                                <div className="absolute inset-0" style={{
                                    backgroundImage: `radial-gradient(circle at 2px 2px, white 1px, transparent 0)`,
                                    backgroundSize: '24px 24px'
                                }}></div>
                            </div>
                            
                            <div className="relative z-10">
                                <div className="flex items-start gap-4 mb-4">
                                    <div className="flex-shrink-0 w-12 h-12 bg-white/20 backdrop-blur-md rounded-2xl flex items-center justify-center shadow-lg">
                                        <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                                        </svg>
                                    </div>
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2 mb-2">
                                            <h2 className="text-xl lg:text-2xl font-bold">AI 추천 설명</h2>
                                            <span className="px-2 py-0.5 bg-white/20 backdrop-blur-sm rounded-full text-xs font-medium">
                                                ✨ AI Generated
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                
                                <div className="bg-white/10 backdrop-blur-md rounded-xl p-5 lg:p-6 border border-white/20 shadow-lg">
                                    <p className="text-white/95 whitespace-pre-wrap leading-relaxed text-base lg:text-lg font-medium">
                                        {result.recommendation}
                                    </p>
                                </div>
                                
                                {/* 장식 요소 */}
                                <div className="absolute top-4 right-4 w-20 h-20 bg-white/5 rounded-full blur-2xl"></div>
                                <div className="absolute bottom-4 left-4 w-32 h-32 bg-pink-500/20 rounded-full blur-3xl"></div>
                            </div>
                        </section>
                    )}

                    {/* 추천 차량 목록 */}
                    {result.cars && result.cars.length > 0 && (
                        <section className="modern-card p-6 lg:p-8">
                            <h2 className="font-bold text-2xl mb-6">추천 차량 ({result.cars.length}대)</h2>
                            <div className="space-y-4">
                                {result.cars.map((car, idx) => (
                                    <div key={car.carId} style={{ animationDelay: `${idx * 0.1}s` }} className="animate-fade-in">
                                        <RecommendedCarCard car={car} />
                                    </div>
                                ))}
                            </div>
                        </section>
                    )}

                    {result.cars && result.cars.length === 0 && (
                        <section className="modern-card p-12 text-center">
                            <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <p className="text-gray-600 text-lg mb-2">조건에 맞는 차량을 찾지 못했습니다</p>
                            <p className="text-sm text-gray-500">다른 조건으로 시도해보세요</p>
                        </section>
                    )}
                </div>
            )}
        </div>
    )
}

function RecommendedCarCard({ car }: { car: RecommendedCar }) {
    const imageUrl = (car as any).imageUrl as string | undefined
    const modelCode = (car as any).modelCode as string | undefined
    
    // 좋아요 상태
    const [likeInfo, setLikeInfo] = useState<LikeInfo>({ count: 0, liked: false })
    const [isLiking, setIsLiking] = useState(false)
    
    // 초기 이미지 설정
    const getInitialImage = () => {
        if (imageUrl && imageUrl.trim() !== '') {
            return imageUrl
        } else if (modelCode) {
            return `/image/car/model/${modelCode}.webp`
        }
        return '/image/car/noimage/no_image.png'
    }
    
    const [imgSrc, setImgSrc] = useState<string>(getInitialImage())
    const [imgLoaded, setImgLoaded] = useState(false)
    const [imgError, setImgError] = useState(false)
    const [isExtracting, setIsExtracting] = useState(false)
    const [isCrawledImage, setIsCrawledImage] = useState(() => {
        if (imageUrl) {
            return imageUrl.startsWith('http://') || 
                   imageUrl.startsWith('https://') ||
                   imageUrl.includes('encar.com') ||
                   imageUrl.includes('chachacha') ||
                   imageUrl.includes('chutcha') ||
                   imageUrl.includes('kcar.com') ||
                   imageUrl.includes('tcar') ||
                   imageUrl.includes('charancha')
        }
        return false
    })
    
    // 좋아요 정보 로드 (서버에서 개수와 개인 좋아요 여부 모두 가져오기)
    useEffect(() => {
        getLikeInfo(car.carId)
            .then((info) => {
                setLikeInfo({
                    count: info.count, // 서버에서 받은 개수
                    liked: info.liked, // 서버에서 받은 개인 좋아요 여부 (IP 기반)
                })
            })
            .catch(() => {
                // 에러 무시 (Redis가 없을 수도 있음)
                setLikeInfo({
                    count: 0,
                    liked: false,
                })
            })
    }, [car.carId])
    
    // 좋아요 토글 핸들러
    const handleLikeToggle = async (e: React.MouseEvent) => {
        e.preventDefault()
        e.stopPropagation()
        
        if (isLiking) return
        
        setIsLiking(true)
        try {
            const response = await toggleLike(car.carId)
            // 서버에서 반환한 값 그대로 사용 (서버가 단일 소스)
            setLikeInfo({
                count: response.count, // 서버에서 받은 개수
                liked: response.liked, // 서버에서 받은 개인 좋아요 여부
            })
        } catch (err) {
            console.warn('Failed to toggle like:', err)
            // 에러 발생 시 서버에서 다시 조회
            getLikeInfo(car.carId)
                .then((info) => {
                    setLikeInfo({
                        count: info.count,
                        liked: info.liked,
                    })
                })
                .catch(() => {
                    // 에러 무시
                })
        } finally {
            setIsLiking(false)
        }
    }

    // 가격은 만원 단위
    const price = car.price ? `${car.price.toLocaleString()}만원` : '가격정보 없음'
    
    // URL이 있으면 외부 링크, 없으면 내부 링크
    const hasExternalUrl = car.url && car.url.trim() !== ''
    const isInternalUrl = car.url && (car.url.startsWith('/') || !car.url.startsWith('http'))
    const internalPath = isInternalUrl ? car.url : `/cars/${car.carId}`

    // 이미지가 크롤링 이미지인지 확인하는 함수
    const checkIfCrawledImage = (url: string): boolean => {
        if (!url) return false
        return url.startsWith('http://') || 
               url.startsWith('https://') ||
               url.includes('encar.com') ||
               url.includes('chachacha') ||
               url.includes('chutcha') ||
               url.includes('kcar.com') ||
               url.includes('tcar') ||
               url.includes('charancha')
    }
    
    // 이미지 로드 - mUrl이나 pcUrl에서 이미지 추출 (no image 피하기)
    useEffect(() => {
        setImgLoaded(false)
        setImgError(false)
        
        // 1. 백엔드에서 받은 이미지 URL이 있으면 사용
        if (imageUrl && imageUrl.trim() !== '') {
            setIsCrawledImage(checkIfCrawledImage(imageUrl))
            setImgSrc(imageUrl)
            return
        }
        
        // 2. 플랫폼 URL(mUrl 또는 pcUrl)에서 이미지 추출 - 반드시 이미지를 찾아야 함
        if (car.url && (car.url.startsWith('http://') || car.url.startsWith('https://'))) {
            setIsExtracting(true)
            extractImageFromUrl(car.url)
                .then((extractedUrl) => {
                    // 추출된 URL이 있고, 로딩/placeholder/spinner가 아닌 경우 사용
                    if (extractedUrl && 
                        extractedUrl.trim() !== '' &&
                        !extractedUrl.toLowerCase().includes('loading') && 
                        !extractedUrl.toLowerCase().includes('placeholder') &&
                        !extractedUrl.toLowerCase().includes('spinner') &&
                        !extractedUrl.toLowerCase().includes('data:image/svg')) {
                        setIsCrawledImage(true) // 추출한 이미지는 크롤링 이미지
                        setImgSrc(extractedUrl)
                    } else {
                        // 추출 실패 시 modelCode 사용
                        console.warn('Image extraction returned invalid URL:', extractedUrl)
                        if (modelCode) {
                            setIsCrawledImage(false)
                            setImgSrc(`/image/car/model/${modelCode}.webp`)
                        } else {
                            setIsCrawledImage(false)
                            setImgSrc('/image/car/noimage/no_image.png')
                        }
                    }
                })
                .catch((err) => {
                    console.warn('Failed to extract image from URL:', car.url, err)
                    // 에러 발생 시 modelCode 사용
                    if (modelCode) {
                        setIsCrawledImage(false)
                        setImgSrc(`/image/car/model/${modelCode}.webp`)
                    } else {
                        setIsCrawledImage(false)
                        setImgSrc('/image/car/noimage/no_image.png')
                    }
                })
                .finally(() => {
                    setIsExtracting(false)
                })
            return
        }
        
        // 3. modelCode로 로컬 이미지 사용
        if (modelCode) {
            setIsCrawledImage(false)
            setImgSrc(`/image/car/model/${modelCode}.webp`)
            return
        }
        
        // 4. 기본 이미지 (최후의 수단)
        setIsCrawledImage(false)
        setImgSrc('/image/car/noimage/no_image.png')
    }, [imageUrl, car.url, modelCode])

    const cardContent = (
        <div className="block modern-card p-5 hover-lift group">
            <div className="flex flex-col sm:flex-row gap-5">
                {/* 이미지 */}
                <div className="w-full sm:w-40 h-32 sm:h-32 bg-gradient-to-br from-gray-100 to-gray-200 overflow-hidden rounded-xl flex-shrink-0 shadow-inner relative">
                    {/* 흐릿한 배경 이미지 (blur) */}
                    {imgSrc && !imgError && imgSrc !== '/image/car/noimage/no_image.png' && (
                        <img
                            src={imgSrc}
                            alt=""
                            className={`absolute inset-0 w-full h-full object-cover blur-md scale-110 transition-opacity duration-300 ${
                                imgLoaded ? 'opacity-30' : 'opacity-0'
                            }`}
                            onLoad={() => {
                                if (!imgLoaded) setImgLoaded(true)
                            }}
                            onError={() => {
                                if (!imgError) {
                                    setImgError(true)
                                    setImgSrc('/image/car/noimage/no_image.png')
                                }
                            }}
                        />
                    )}
                    {/* 메인 이미지 - 크롤링한 이미지는 약간 흐릿하게 (법적 이슈 방지) */}
                    {imgSrc && (
                        <img
                            src={imgSrc}
                            alt={`${car.maker} ${car.model}`}
                            className="relative w-full h-full object-cover group-hover:scale-110 transition-all duration-300"
                            style={isCrawledImage ? { filter: 'blur(2px)' } : {}}
                            onLoad={() => {
                                if (!imgLoaded) setImgLoaded(true)
                            }}
                            onError={() => {
                                if (!imgError) {
                                    setImgError(true)
                                    setIsCrawledImage(false)
                                    setImgSrc('/image/car/noimage/no_image.png')
                                }
                            }}
                        />
                    )}
                    {/* 크롤링 이미지 표시 배지 */}
                    {isCrawledImage && imgLoaded && (
                        <div className="absolute top-2 right-2 bg-black/50 text-white text-[10px] px-2 py-1 rounded backdrop-blur-sm">
                            참고용
                        </div>
                    )}
                </div>

                {/* 정보 */}
                <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-4 mb-3">
                        <div className="flex-1 min-w-0">
                            <h3 className="font-bold text-xl leading-tight mb-2 group-hover:text-blue-600 transition">
                                {car.maker} {car.model} {car.trim || ''}
                            </h3>
                            <div className="flex flex-wrap items-center gap-3 text-sm text-gray-600 mb-3">
                                {car.year && <span>{car.year}년식</span>}
                                {car.mileage && <span>{car.mileage.toLocaleString()}km</span>}
                                {car.fuel && <span>{car.fuel}</span>}
                                {car.transmission && <span>{car.transmission}</span>}
                            </div>
                            <div className="flex items-center justify-between mb-3">
                                <div className="text-2xl font-bold text-blue-600">{price}</div>
                                <div className="flex items-center gap-3">
                                    {/* 개인 좋아요 버튼 (하트만) */}
                                    <button
                                        onClick={handleLikeToggle}
                                        disabled={isLiking}
                                        className={`flex items-center justify-center w-8 h-8 rounded-lg transition-all duration-200 ${
                                            likeInfo.liked
                                                ? 'bg-red-50 text-red-600 hover:bg-red-100'
                                                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                                        } ${isLiking ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                                        title={likeInfo.liked ? '좋아요 취소' : '좋아요'}
                                    >
                                        <svg 
                                            className={`w-5 h-5 transition-transform ${isLiking ? 'animate-pulse' : ''}`}
                                            fill={likeInfo.liked ? 'currentColor' : 'none'}
                                            stroke="currentColor" 
                                            viewBox="0 0 24 24"
                                        >
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                                        </svg>
                                    </button>
                                    {/* 전체 좋아요 개수 (오른쪽) */}
                                    <div className="flex items-center gap-1 text-sm text-gray-600">
                                        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                                            <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                                        </svg>
                                        <span className="font-medium">{likeInfo.count}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                        {car.relevanceScore !== undefined && car.relevanceScore !== null && (
                            <div className="flex-shrink-0">
                                <div className="px-3 py-1.5 bg-gradient-to-br from-blue-500 to-purple-500 text-white text-xs font-semibold rounded-lg">
                                    유사도 {Math.round(car.relevanceScore * 100)}%
                                </div>
                            </div>
                        )}
                    </div>

                    {/* 추천 이유 */}
                    {car.reason && (
                        <div className="pt-4 border-t border-gray-200">
                            <p className="text-sm text-gray-700">
                                <span className="font-semibold text-gray-900">추천 이유:</span> {car.reason}
                            </p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    )

    // 외부 URL이 있으면 외부 링크, 없으면 내부 링크
    if (hasExternalUrl && !isInternalUrl) {
        return (
            <a
                href={car.url}
                target="_blank"
                rel="noopener noreferrer"
                className="block"
            >
                {cardContent}
            </a>
        )
    }

    return (
        <Link to={internalPath}>
            {cardContent}
        </Link>
    )
}
