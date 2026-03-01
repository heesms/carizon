import React, { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams, useLocation, type Location } from 'react-router-dom'
import { getRecommendations, type RecommendationResponse, type RecommendedCar } from '@/api/recommendations'
import { toggleLike, getLike } from '@/api/likes'
import AdSlot from '@/components/AdSlot'
import { trackAiRecommendation, trackAiPromptClick } from '@/lib/analytics'
import CarImagePlaceholder from '@/components/CarImagePlaceholder'

type Message =
  | { role: 'user'; text: string }
  | { role: 'assistant'; text: string; cars?: RecommendedCar[]; query?: string }
  | { role: 'loading' }

const LOADING_MESSAGES = [
  '매물 데이터를 분석하고 있어요...',
  '조건에 맞는 차량을 찾고 있어요...',
  '가격 경쟁력을 비교하고 있어요...',
  '최적의 추천 조합을 만들고 있어요...',
  '거의 다 됐어요! 잠시만 기다려주세요...',
]

const QUICK_PROMPTS = [
  '가족 4인용 SUV, 예산 2000만원대',
  '출퇴근용 연비 좋은 소형차',
  '첫차로 무사고 국산차 추천',
  '3000만원 이하 수입차',
  '전기차 or 하이브리드 추천',
]


const INITIAL_TEXT = '안녕하세요! 🚗 원하시는 차량 조건을 자유롭게 말씀해주세요.\n\n예산, 용도, 연료 종류, 차체 타입 등을 알려주시면 딱 맞는 중고차를 추천해드릴게요!'
const INITIAL_MESSAGE: Message = { role: 'assistant', text: INITIAL_TEXT }

function useIsMobile() {
  const [m, setM] = useState(false)
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 767px)')
    const u = () => setM(mq.matches)
    u(); mq.addEventListener('change', u)
    return () => mq.removeEventListener('change', u)
  }, [])
  return m
}

export default function Recommendation() {
  const [sp] = useSearchParams()
  const fromLocation = useLocation()
  const queryParam = (sp.get('query') ?? '').trim()
  const isMobile = useIsMobile()
  const [messages, setMessages] = useState<Message[]>([INITIAL_MESSAGE])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const autoSentQueryRef = useRef('')

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async (text?: string) => {
    const query = (text ?? input).trim()
    if (!query || loading) return
    setInput('')
    // textarea 높이 초기화
    if (inputRef.current) inputRef.current.style.height = 'auto'
    trackAiRecommendation(query, text === queryParam ? 'url' : text ? 'quick_prompt' : 'input')

    setMessages(prev => [...prev, { role: 'user', text: query }, { role: 'loading' }])
    setLoading(true)

    try {
      const res: RecommendationResponse = await getRecommendations({ query, maxResults: 5 })
      setMessages(prev => [
        ...prev.filter(m => m.role !== 'loading'),
        {
          role: 'assistant',
          text: res.recommendation ?? '추천 결과를 확인해보세요.',
          cars: res.cars ?? [],
          query,
        },
      ])
    } catch {
      setMessages(prev => [
        ...prev.filter(m => m.role !== 'loading'),
        { role: 'assistant', text: '죄송합니다. AI 추천 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.' },
      ])
    } finally {
      setLoading(false)
      setTimeout(() => inputRef.current?.focus(), 100)
    }
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() }
  }

  const onInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value)
    // auto-resize
    e.target.style.height = 'auto'
    e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px'
  }

  // URL query 자동 전송은 동일 문구 1회만 처리
  useEffect(() => {
    if (!queryParam) { autoSentQueryRef.current = ''; return }
    if (loading) return
    if (autoSentQueryRef.current === queryParam) return
    autoSentQueryRef.current = queryParam
    handleSend(queryParam)
  }, [queryParam, loading])

  return (
    <div className="max-w-3xl mx-auto animate-fade-in pb-44">
      {/* 헤더 */}
      <div className="mb-4 sm:mb-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 sm:w-10 sm:h-10 bg-gradient-to-br from-brand-500 to-brand-700 rounded-xl flex items-center justify-center text-lg sm:text-xl shadow-sm">
            🤖
          </div>
          <div>
            <h1 className="text-lg sm:text-xl font-black text-gray-900">AI 차량 추천</h1>
            <p className="text-[11px] sm:text-xs text-gray-400">Powered by Carizon</p>
          </div>
        </div>
      </div>

      {/* 메시지 영역 */}
      <div className="space-y-4 sm:space-y-5">
        {/* 빠른 프롬프트 (초기 + 대화 적을 때) */}
        {messages.length <= 1 && (
          <div className="space-y-2">
            <p className="text-xs text-gray-400 font-medium">빠른 질문</p>
            <div className="flex flex-wrap gap-1.5 sm:gap-2">
              {QUICK_PROMPTS.map(p => (
                <button
                  key={p}
                  onClick={() => { trackAiPromptClick(p); handleSend(p) }}
                  className="text-xs sm:text-sm px-2.5 sm:px-3 py-1.5 rounded-full bg-brand-50 text-brand-700 border border-brand-100 hover:bg-brand-100 transition-colors font-medium"
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, i) => {
          if (msg.role === 'loading') return <LoadingBubble key={i} />

          if (msg.role === 'user') return (
            <div key={i} className="flex justify-end gap-2 sm:gap-3">
              <div className="max-w-[85%] sm:max-w-[78%] bg-brand-600 text-white rounded-2xl rounded-tr-sm px-3 sm:px-4 py-2.5 sm:py-3 text-sm leading-relaxed shadow-sm">
                {msg.text}
              </div>
              <div className="w-7 h-7 sm:w-8 sm:h-8 bg-gray-100 rounded-full flex items-center justify-center text-sm shrink-0 border border-gray-200">
                👤
              </div>
            </div>
          )

          // assistant 메시지
          const hasCars = msg.cars && msg.cars.length > 0
          const isLast = i === messages.length - 1

          return (
            <div key={i} className="flex gap-2 sm:gap-3">
              <div className="w-7 h-7 sm:w-8 sm:h-8 bg-gradient-to-br from-brand-400 to-brand-600 rounded-full flex items-center justify-center text-sm shrink-0 shadow-sm">
                🤖
              </div>
              <div className="flex-1 space-y-2 sm:space-y-3 min-w-0">
                {/* AI 텍스트 버블 */}
                <div className="bg-white border border-gray-100 border-l-[3px] border-l-brand-400 rounded-2xl rounded-tl-sm px-3 sm:px-4 py-2.5 sm:py-3 text-sm leading-relaxed whitespace-pre-wrap text-gray-700 shadow-sm">
                  {msg.text}
                </div>

                {/* 차량 카드 목록 */}
                {hasCars && (
                  <div className="space-y-2 sm:space-y-3">
                    {msg.cars!
                      .filter((car, ci, arr) => arr.findIndex(c => c.carId === car.carId) === ci)
                      .map((car, ci) => (
                        <RecommendedCarCard
                          key={car.carId ?? ci}
                          car={car}
                          isMobile={isMobile}
                          rank={ci + 1}
                          fromLocation={fromLocation}
                        />
                      ))}
                  </div>
                )}

                {/* 결과 하단: 검색에서 더 보기 */}
                {hasCars && isLast && !loading && (
                  <Link
                    to={`/search?q=${encodeURIComponent((msg as { query?: string }).query ?? '')}`}
                    className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-brand-600 transition-colors group w-fit pt-1"
                  >
                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    검색에서 더 찾아보기 →
                  </Link>
                )}
              </div>
            </div>
          )
        })}

        <div ref={bottomRef} />
      </div>

      {/* 하단 고정: 광고 + 입력창 */}
      <div className="fixed bottom-14 sm:bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-sm border-t border-gray-100 shadow-[0_-4px_20px_rgba(0,0,0,0.07)]">
        <div className="max-w-3xl mx-auto px-4 pt-2">
          <AdSlot id="recommendation-banner" variant="leaderboard" />
          <div className="flex gap-2 items-end py-2">
            <textarea
              ref={inputRef}
              value={input}
              onChange={onInputChange}
              onKeyDown={onKeyDown}
              placeholder="원하는 차량 조건을 입력하세요..."
              rows={1}
              style={{ minHeight: '40px', maxHeight: '120px' }}
              className="input resize-none flex-1 text-sm leading-relaxed overflow-hidden"
              disabled={loading}
            />
            <button
              onClick={() => handleSend()}
              disabled={loading || !input.trim()}
              className="btn-primary px-3 sm:px-4 py-2.5 self-end disabled:opacity-40 disabled:cursor-not-allowed shrink-0 transition-all"
              aria-label="전송"
            >
              {loading ? (
                <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z" />
                </svg>
              ) : (
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              )}
            </button>
          </div>
          {!isMobile && (
            <p className="text-[10px] text-gray-300 pb-2 text-center">
              Enter로 전송 · Shift+Enter 줄바꿈 · AI 추천은 참고용이에요
            </p>
          )}
          {isMobile && (
            <p className="text-[10px] text-gray-300 pb-2 text-center">
              AI 추천은 참고용이며, 거래 전 반드시 직접 확인하세요
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

// ── 순위별 스타일 설정 ─────────────────────────────────────────────────────────
type RankStyle = {
  headerBg: string; cardBg: string; border: string
  priceClass: string; nameClass: string; btnClass: string
  imgClass: string; medal: string; label: string; badge?: string
}
const RANK_STYLES: Record<number, RankStyle> = {
  1: {
    headerBg:   'bg-gradient-to-r from-amber-400 to-yellow-500',
    cardBg:     'bg-gradient-to-b from-amber-50/60 to-white',
    border:     'border-2 border-amber-300',
    priceClass: 'text-sm sm:text-base font-black text-amber-700',
    nameClass:  'text-sm sm:text-[15px] font-black text-gray-900',
    btnClass:   'bg-amber-500 text-white hover:bg-amber-600',
    imgClass:   'w-24 h-16 sm:w-28 sm:h-20',
    medal: '🥇', label: '1위 추천', badge: 'AI BEST',
  },
  2: {
    headerBg:   'bg-gradient-to-r from-slate-400 to-gray-500',
    cardBg:     'bg-gradient-to-b from-slate-50/60 to-white',
    border:     'border-2 border-slate-300',
    priceClass: 'text-sm font-black text-slate-600',
    nameClass:  'text-sm font-black text-gray-900',
    btnClass:   'bg-slate-600 text-white hover:bg-slate-700',
    imgClass:   'w-20 h-14 sm:w-24 sm:h-16',
    medal: '🥈', label: '2위 추천',
  },
  3: {
    headerBg:   'bg-gradient-to-r from-orange-400 to-amber-500',
    cardBg:     'bg-gradient-to-b from-orange-50/60 to-white',
    border:     'border border-orange-300',
    priceClass: 'text-xs sm:text-sm font-bold text-orange-700',
    nameClass:  'text-xs sm:text-sm font-bold text-gray-900',
    btnClass:   'bg-orange-500 text-white hover:bg-orange-600',
    imgClass:   'w-20 h-14 sm:w-24 sm:h-16',
    medal: '🥉', label: '3위 추천',
  },
}

function RecommendedCarCard({
  car,
  isMobile,
  rank,
  fromLocation,
}: {
  car: RecommendedCar
  isMobile: boolean
  rank: number
  fromLocation: Location
}) {
  const [liked, setLiked]   = useState(false)
  const [imgSrc, setImgSrc] = useState<string | null>(car.imageUrl || null)

  useEffect(() => {
    if (!car.carId) return
    getLike(car.carId).then(r => setLiked(r.liked)).catch(() => {})
  }, [car.carId])

  const handleLike = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!car.carId) return
    const res = await toggleLike(car.carId).catch(() => null)
    if (res) setLiked(res.liked)
  }

  const carState = {
    from: `${fromLocation.pathname}${fromLocation.search}${fromLocation.hash}`,
    source: 'ai',
    backgroundLocation: fromLocation,
  }

  // 지역 짧은 표현 (앞 1~2 토큰만)
  const shortRegion = car.region ? car.region.split(' ').slice(0, 1).join(' ') : null

  const cfg = RANK_STYLES[rank]

  // ── 1~3위: 메달 헤더 강조 카드 ──────────────────────────────────────────────
  if (cfg) {
    return (
      <div className={`relative rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition-all duration-200 cursor-pointer ${cfg.border} ${cfg.cardBg}`}>
        {car.carId && (
          <Link
            to={`/cars/${car.carId}`}
            state={carState}
            className="absolute inset-0 z-10"
            aria-label={`${car.maker} ${car.model} 상세보기`}
          />
        )}

        {/* 순위 헤더 배너 */}
        <div className={`${cfg.headerBg} px-3 py-1.5 flex items-center gap-2`}>
          <span className="text-base leading-none">{cfg.medal}</span>
          <span className="text-white text-xs font-black tracking-wide">{cfg.label}</span>
          {cfg.badge && (
            <span className="ml-auto text-[10px] font-bold bg-white/25 text-white px-2 py-0.5 rounded-full">
              {cfg.badge}
            </span>
          )}
        </div>

        {/* 카드 바디 */}
        <div className="p-3 sm:p-4 flex gap-3">
          {/* 이미지 */}
          <div className={`${cfg.imgClass} rounded-xl overflow-hidden bg-gray-100 shrink-0`}>
            {imgSrc ? (
              <img src={imgSrc} alt={`${car.maker} ${car.model}`}
                className="w-full h-full object-cover object-[center_65%]"
                onError={() => setImgSrc(null)} />
            ) : (
              <CarImagePlaceholder />
            )}
          </div>

          {/* 정보 */}
          <div className="flex-1 min-w-0">
            <div className={`${cfg.nameClass} truncate`}>
              {car.maker} {car.model}
              {car.trim && <span className="font-normal text-gray-400 ml-1 text-[10px] sm:text-xs">{car.trim}</span>}
            </div>
            <div className="flex flex-wrap gap-1 mt-1">
              {car.year    && <span className="badge badge-gray text-[10px]">{car.year}년</span>}
              {car.mileage && <span className="badge badge-gray text-[10px]">{car.mileage.toLocaleString()}km</span>}
              {car.fuel    && <span className="badge badge-blue text-[10px]">{car.fuel}</span>}
              {shortRegion && <span className="badge badge-gray text-[10px]">📍{shortRegion}</span>}
            </div>
            {car.reason && (
              <p className="text-[10px] sm:text-xs text-gray-500 mt-1.5 line-clamp-2 flex gap-1">
                <span className="text-brand-400 shrink-0">💡</span>
                <span>{car.reason}</span>
              </p>
            )}
            {car.price && (
              <div className={`${cfg.priceClass} mt-1.5`}>{car.price.toLocaleString()}만원</div>
            )}
          </div>

          {/* 좋아요 (커버 링크 위 z-20) */}
          <div className="shrink-0 relative z-20">
            {car.carId && (
              <button onClick={handleLike}
                className={`flex items-center justify-center w-7 h-7 rounded-lg transition-colors
                  ${liked ? 'text-red-500 bg-red-50' : 'text-gray-300 hover:text-red-400 hover:bg-red-50'}`}>
                <svg className="w-3.5 h-3.5" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>
    )
  }

  // ── 4위 이하: 컴팩트 카드 ──────────────────────────────────────────────────
  return (
    <div className="relative bg-white border border-gray-100 rounded-2xl p-2.5 sm:p-3 flex gap-2 sm:gap-3 hover:shadow-sm hover:border-gray-200 transition-all duration-150 cursor-pointer">
      {car.carId && (
        <Link
          to={`/cars/${car.carId}`}
          state={carState}
          className="absolute inset-0 z-10 rounded-2xl"
          aria-label={`${car.maker} ${car.model} 상세보기`}
        />
      )}

      {/* 순위 뱃지 */}
      <div className="w-5 h-5 sm:w-6 sm:h-6 rounded-full bg-gray-100 text-gray-400 text-[10px] font-bold flex items-center justify-center shrink-0 mt-0.5">
        {rank}
      </div>

      {/* 이미지 */}
      <div className="w-16 h-12 sm:w-20 sm:h-14 rounded-lg overflow-hidden bg-gray-100 shrink-0">
        {imgSrc ? (
          <img src={imgSrc} alt={`${car.maker} ${car.model}`}
            className="w-full h-full object-cover object-[center_65%]"
            onError={() => setImgSrc(null)} />
        ) : (
          <CarImagePlaceholder />
        )}
      </div>

      {/* 정보 */}
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-xs sm:text-sm text-gray-900 truncate">
          {car.maker} {car.model}
          {car.trim && <span className="font-normal text-gray-400 ml-1 text-[10px]">{car.trim}</span>}
        </div>
        <div className="flex flex-wrap gap-1 mt-0.5">
          {car.year    && <span className="badge badge-gray text-[10px]">{car.year}년</span>}
          {car.mileage && <span className="badge badge-gray text-[10px]">{car.mileage.toLocaleString()}km</span>}
          {car.fuel    && <span className="badge badge-blue text-[10px]">{car.fuel}</span>}
          {shortRegion && <span className="badge badge-gray text-[10px]">📍{shortRegion}</span>}
        </div>
        {car.reason && (
          <p className="text-[10px] text-gray-400 mt-0.5 line-clamp-2 flex gap-1">
            <span className="text-brand-400 shrink-0">💡</span>
            <span>{car.reason}</span>
          </p>
        )}
        {car.price && (
          <div className="text-xs font-bold text-brand-600 mt-0.5">{car.price.toLocaleString()}만원</div>
        )}
      </div>

      {/* 좋아요 */}
      <div className="shrink-0 relative z-20 flex flex-col items-end">
        {car.carId && (
          <button onClick={handleLike}
            className={`flex items-center justify-center w-6 h-6 rounded-lg transition-colors
              ${liked ? 'text-red-500 bg-red-50' : 'text-gray-300 hover:text-red-400 hover:bg-red-50'}`}>
            <svg className="w-3 h-3" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
            </svg>
          </button>
        )}
      </div>
    </div>
  )
}

function LoadingBubble() {
  const [msgIdx, setMsgIdx] = useState(0)
  const [elapsed, setElapsed] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => {
      setElapsed(prev => prev + 1)
      setMsgIdx(prev => (prev + 1) % LOADING_MESSAGES.length)
    }, 3000)
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="flex gap-2 sm:gap-3">
      <div className="w-7 h-7 sm:w-8 sm:h-8 bg-gradient-to-br from-brand-400 to-brand-600 rounded-full flex items-center justify-center text-sm shrink-0 shadow-sm">🤖</div>
      <div className="bg-white border border-gray-100 border-l-[3px] border-l-brand-400 rounded-2xl rounded-tl-sm px-3 sm:px-4 py-2.5 sm:py-3 space-y-2 shadow-sm">
        <div className="flex gap-1.5 items-center">
          {[0, 1, 2].map(d => (
            <div key={d} className="w-2 h-2 bg-brand-400 rounded-full animate-bounce"
              style={{ animationDelay: `${d * 0.15}s` }} />
          ))}
        </div>
        <p className="text-xs text-gray-500 animate-pulse">{LOADING_MESSAGES[msgIdx]}</p>
        {elapsed >= 2 && (
          <div className="w-40 sm:w-56 bg-gray-100 rounded-full h-1">
            <div className="bg-gradient-to-r from-brand-400 to-brand-600 h-1 rounded-full transition-all duration-1000"
              style={{ width: `${Math.min(95, elapsed * 12)}%` }} />
          </div>
        )}
      </div>
    </div>
  )
}
