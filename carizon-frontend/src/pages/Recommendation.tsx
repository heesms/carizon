import React, { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams, useLocation, type Location } from 'react-router-dom'
import { getRecommendations, type RecommendationResponse, type RecommendedCar } from '@/api/recommendations'
import { toggleLike, getLike } from '@/api/likes'
import AdSlot from '@/components/AdSlot'
import { trackAiRecommendation, trackAiPromptClick } from '@/lib/analytics'

const NO_IMAGE = `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300"><rect fill="#f3f4f6" width="400" height="300"/><text fill="#9ca3af" font-family="sans-serif" font-size="13" x="200" y="158" text-anchor="middle">이미지 없음</text><rect fill="#e5e7eb" x="170" y="110" width="60" height="38" rx="4"/></svg>')}`

type Message =
  | { role: 'user'; text: string }
  | { role: 'assistant'; text: string; cars?: RecommendedCar[] }
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
  const [messages, setMessages] = useState<Message[]>([
    {
      role: 'assistant',
      text: '안녕하세요! 🚗 원하시는 차량 조건을 자유롭게 말씀해주세요.\n\n예산, 용도, 연료 종류, 차체 타입 등을 알려주시면 딱 맞는 중고차를 추천해드릴게요!',
    },
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const inputRef  = useRef<HTMLTextAreaElement>(null)
  const autoSentQueryRef = useRef('')

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async (text?: string) => {
    const query = (text ?? input).trim()
    if (!query || loading) return
    setInput('')
    trackAiRecommendation(query, text === queryParam ? 'url' : text ? 'quick_prompt' : 'input')

    setMessages(prev => [...prev, { role: 'user', text: query }, { role: 'loading' }])
    setLoading(true)

    try {
      const res: RecommendationResponse = await getRecommendations({ query, maxResults: 5 })
      setMessages(prev => [
        ...prev.filter(m => m.role !== 'loading'),
        { role: 'assistant', text: res.recommendation ?? '추천 결과를 확인해보세요.', cars: res.cars ?? [] },
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

  // URL query 자동 전송은 동일 문구 1회만 처리 (dev strict mode 중복 실행 방지)
  useEffect(() => {
    if (!queryParam) {
      autoSentQueryRef.current = ''
      return
    }
    if (loading) return
    if (autoSentQueryRef.current === queryParam) return
    autoSentQueryRef.current = queryParam
    handleSend(queryParam)
  }, [queryParam, loading])

  return (
    <div className="max-w-3xl mx-auto animate-fade-in pb-40">
      {/* 헤더 */}
      <div className="mb-4 sm:mb-5">
        <div className="flex items-center gap-3 mb-1">
          <div className="w-9 h-9 sm:w-10 sm:h-10 bg-brand-600 rounded-xl flex items-center justify-center text-lg sm:text-xl">🤖</div>
          <div>
            <h1 className="text-lg sm:text-xl font-black text-gray-900">AI 차량 추천</h1>
            <p className="text-[11px] sm:text-xs text-gray-400">Powered by Carizon</p>
          </div>
        </div>
      </div>

      {/* 메시지 영역 - 페이지 스크롤 사용 */}
      <div className="space-y-4 sm:space-y-5">
        {/* 빠른 프롬프트 (초기 상태) */}
        {messages.length === 1 && (
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
              <div className="max-w-[85%] sm:max-w-[80%] bg-brand-600 text-white rounded-2xl rounded-tr-sm px-3 sm:px-4 py-2.5 sm:py-3 text-sm leading-relaxed">
                {msg.text}
              </div>
              <div className="w-7 h-7 sm:w-8 sm:h-8 bg-gray-200 rounded-full flex items-center justify-center text-sm shrink-0">👤</div>
            </div>
          )

          return (
            <div key={i} className="flex gap-2 sm:gap-3">
              <div className="w-7 h-7 sm:w-8 sm:h-8 bg-brand-100 rounded-full flex items-center justify-center text-sm shrink-0">🤖</div>
              <div className="flex-1 space-y-2 sm:space-y-3 max-w-[90%] sm:max-w-[85%]">
                <div className="bg-gray-50 border border-gray-100 rounded-2xl rounded-tl-sm px-3 sm:px-4 py-2.5 sm:py-3 text-sm leading-relaxed whitespace-pre-wrap text-gray-700">
                  {msg.text}
                </div>
                {msg.cars && msg.cars.length > 0 && (
                  <div className="space-y-2 sm:space-y-3">
                    {msg.cars
                      .filter((car, i, arr) => arr.findIndex(c => c.carId === car.carId) === i)
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
              </div>
            </div>
          )
        })}
        <div ref={bottomRef} />
      </div>

      {/* 하단 고정: 광고 + 입력창 */}
      <div className="fixed bottom-0 left-0 right-0 z-40 bg-white border-t border-gray-100 shadow-[0_-4px_20px_rgba(0,0,0,0.07)]">
        <div className="max-w-3xl mx-auto px-4 pt-2">
          <AdSlot id="recommendation-banner" variant="leaderboard" />
          <div className="flex gap-2 items-end py-2">
            <textarea
              ref={inputRef}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder={isMobile ? "원하는 차량 조건을 입력하세요..." : "원하는 차량 조건을 입력하세요... (Enter로 전송, Shift+Enter 줄바꿈)"}
              rows={isMobile ? 1 : 2}
              className="input resize-none flex-1 text-sm leading-relaxed"
              disabled={loading}
            />
            <button
              onClick={() => handleSend()}
              disabled={loading || !input.trim()}
              className="btn-primary px-3 sm:px-4 py-2.5 sm:py-3 self-end disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
            </button>
          </div>
          <p className="text-[10px] text-gray-300 pb-2 text-center">
            AI 추천은 참고용이며, 실제 거래 전 반드시 직접 확인하세요
          </p>
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
    cardBg:     'bg-gradient-to-b from-amber-50 to-white',
    border:     'border-2 border-amber-300',
    priceClass: 'text-sm sm:text-base font-black text-amber-700',
    nameClass:  'text-sm sm:text-[15px] font-black text-gray-900',
    btnClass:   'bg-amber-500 text-white hover:bg-amber-600',
    imgClass:   'w-24 h-16 sm:w-28 sm:h-20',
    medal: '🥇', label: '1위 추천', badge: 'AI BEST',
  },
  2: {
    headerBg:   'bg-gradient-to-r from-slate-400 to-gray-500',
    cardBg:     'bg-gradient-to-b from-slate-50 to-white',
    border:     'border-2 border-slate-300',
    priceClass: 'text-sm font-black text-slate-600',
    nameClass:  'text-sm font-black text-gray-900',
    btnClass:   'bg-slate-600 text-white hover:bg-slate-700',
    imgClass:   'w-20 h-14 sm:w-24 sm:h-16',
    medal: '🥈', label: '2위 추천',
  },
  3: {
    headerBg:   'bg-gradient-to-r from-orange-400 to-amber-500',
    cardBg:     'bg-gradient-to-b from-orange-50 to-white',
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
  const [liked, setLiked]     = useState(false)
  const [imgSrc, setImgSrc]   = useState(car.imageUrl || NO_IMAGE)

  useEffect(() => {
    if (!car.carId) return
    getLike(car.carId).then(r => { setLiked(r.liked) }).catch(() => {})
  }, [car.carId])

  const handleLike = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!car.carId) return
    const res = await toggleLike(car.carId).catch(() => null)
    if (res) { setLiked(res.liked) }
  }

  const carState = {
    from: `${fromLocation.pathname}${fromLocation.search}${fromLocation.hash}`,
    source: 'ai',
    backgroundLocation: fromLocation,
  }

  const cfg = RANK_STYLES[rank]

  // ── 1~3위: 메달 헤더 강조 카드 ──────────────────────────────────────────────
  if (cfg) {
    return (
      <div className={`relative rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition-all cursor-pointer ${cfg.border} ${cfg.cardBg}`}>
        {/* 전체 카드 클릭 커버 링크 */}
        {car.carId && (
          <Link
            to={`/cars/${car.carId}`}
            state={carState}
            className="absolute inset-0 z-0"
            aria-label={`${car.maker} ${car.model} 상세보기`}
          />
        )}

        {/* 순위 헤더 배너 */}
        <div className={`${cfg.headerBg} px-3 py-1.5 flex items-center gap-2 relative`}>
          <span className="text-base leading-none">{cfg.medal}</span>
          <span className="text-white text-xs font-black tracking-wide">{cfg.label}</span>
          {cfg.badge && (
            <span className="ml-auto text-[10px] font-bold bg-white/20 text-white px-2 py-0.5 rounded-full">
              {cfg.badge}
            </span>
          )}
        </div>

        {/* 카드 바디 */}
        <div className="p-3 sm:p-4 flex gap-3 relative">
          {/* 이미지 */}
          <div className={`${cfg.imgClass} rounded-xl overflow-hidden bg-gray-50 shrink-0`}>
            <img src={imgSrc} alt={`${car.maker} ${car.model}`}
              className="w-full h-full object-cover object-[center_65%]"
              onError={() => setImgSrc(NO_IMAGE)} />
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
            </div>
            {car.reason && (
              <p className="text-[10px] sm:text-xs text-gray-500 mt-1 line-clamp-2">{car.reason}</p>
            )}
            {car.price && (
              <div className={`${cfg.priceClass} mt-1`}>{car.price.toLocaleString()}만원</div>
            )}
          </div>

          {/* 좋아요 버튼 (커버 링크 위에 z-10으로 배치) */}
          <div className="flex flex-col items-end gap-1.5 shrink-0 relative z-10">
            {car.carId && (
              <button onClick={handleLike}
                className={`flex items-center justify-center w-7 h-7 rounded-lg transition-colors
                  ${liked ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-400 hover:bg-red-50'}`}>
                <svg className="w-3 h-3 sm:w-3.5 sm:h-3.5" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
                </svg>
              </button>
            )}
          </div>
        </div>

        {/* 상세보기 안내 */}
        <div className="px-3 pb-2.5 flex justify-end relative">
          <span className="text-[10px] text-gray-400">상세보기 →</span>
        </div>
      </div>
    )
  }

  // ── 4위 이하: 컴팩트 카드 ──────────────────────────────────────────────────
  return (
    <div className="relative card p-2.5 sm:p-3 flex gap-2 sm:gap-3 hover:shadow-sm transition-shadow cursor-pointer">
      {/* 전체 카드 클릭 커버 링크 */}
      {car.carId && (
        <Link
          to={`/cars/${car.carId}`}
          state={carState}
          className="absolute inset-0 z-0 rounded-2xl"
          aria-label={`${car.maker} ${car.model} 상세보기`}
        />
      )}

      {/* 순위 뱃지 */}
      <div className="w-5 h-5 sm:w-6 sm:h-6 rounded-full bg-gray-100 text-gray-500 text-[10px] font-bold flex items-center justify-center shrink-0 mt-0.5 relative z-10">
        {rank}
      </div>

      {/* 이미지 */}
      <div className="w-16 h-12 sm:w-20 sm:h-14 rounded-lg overflow-hidden bg-gray-50 shrink-0 relative z-10">
        <img src={imgSrc} alt={`${car.maker} ${car.model}`}
          className="w-full h-full object-cover object-[center_65%]"
          onError={() => setImgSrc(NO_IMAGE)} />
      </div>

      {/* 정보 */}
      <div className="flex-1 min-w-0 relative z-10">
        <div className="font-semibold text-xs sm:text-sm text-gray-900 truncate">
          {car.maker} {car.model}
          {car.trim && <span className="font-normal text-gray-400 ml-1 text-[10px]">{car.trim}</span>}
        </div>
        <div className="flex flex-wrap gap-1 mt-0.5">
          {car.year    && <span className="badge badge-gray text-[10px]">{car.year}년</span>}
          {car.mileage && <span className="badge badge-gray text-[10px]">{car.mileage.toLocaleString()}km</span>}
          {car.fuel    && <span className="badge badge-blue text-[10px]">{car.fuel}</span>}
        </div>
        {car.reason && (
          <p className="text-[10px] text-gray-400 mt-0.5 line-clamp-1">{car.reason}</p>
        )}
        {car.price && (
          <div className="text-xs font-bold text-brand-600 mt-0.5">{car.price.toLocaleString()}만원</div>
        )}
      </div>

      {/* 좋아요 버튼 */}
      <div className="flex flex-col items-end gap-1 shrink-0 relative z-10">
        {car.carId && (
          <button onClick={handleLike}
            className={`flex items-center justify-center w-6 h-6 rounded-lg transition-colors
              ${liked ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-400 hover:bg-red-50'}`}>
            <svg className="w-3 h-3" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
            </svg>
          </button>
        )}
        <span className="text-[9px] text-gray-400 mt-0.5">→ 상세보기</span>
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
      <div className="w-7 h-7 sm:w-8 sm:h-8 bg-brand-100 rounded-full flex items-center justify-center text-sm shrink-0">🤖</div>
      <div className="bg-gray-50 border border-gray-100 rounded-2xl rounded-tl-sm px-3 sm:px-4 py-2.5 sm:py-3 space-y-1.5">
        <div className="flex gap-1.5 items-center">
          {[0, 1, 2].map(d => (
            <div key={d} className="w-2 h-2 bg-brand-400 rounded-full animate-bounce"
              style={{ animationDelay: `${d * 0.15}s` }} />
          ))}
        </div>
        <p className="text-xs text-gray-500 animate-pulse transition-all">{LOADING_MESSAGES[msgIdx]}</p>
        {elapsed >= 2 && (
          <div className="w-full bg-gray-200 rounded-full h-1 mt-1">
            <div className="bg-brand-400 h-1 rounded-full transition-all duration-1000"
              style={{ width: `${Math.min(95, elapsed * 12)}%` }} />
          </div>
        )}
      </div>
    </div>
  )
}
