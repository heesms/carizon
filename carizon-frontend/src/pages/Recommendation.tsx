import React, { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getRecommendations, type RecommendationResponse, type RecommendedCar } from '@/api/recommendations'
import { toggleLike, getLike } from '@/api/likes'
import AdSlot from '@/components/AdSlot'
import CarDetail from '@/pages/CarDetail'

const NO_IMAGE = '/image/car/noimage/no_image.png'
const RECOMMENDATION_CHAT_STORAGE_KEY = 'carizon:recommendation_chat_v1'
const RECOMMENDATION_CHAT_LOCAL_STORAGE_KEY = 'carizon:recommendation_chat_v1_local'
const INITIAL_ASSISTANT_TEXT = '안녕하세요! 🚗 원하시는 차량 조건을 자유롭게 말씀해주세요.\n\n예산, 용도, 연료 종류, 차체 타입 등을 알려주시면 딱 맞는 중고차를 추천해드릴게요!'

type Message =
  | { id: number; role: 'user'; text: string }
  | { id: number; role: 'assistant'; text: string; cars?: RecommendedCar[]; animate?: boolean }
  | { id: number; role: 'loading' }

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
  const navigate = useNavigate()
  const [sp] = useSearchParams()
  const queryParam = (sp.get('query') ?? sp.get('q') ?? '').trim()
  const isMobile = useIsMobile()
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 1,
      role: 'assistant',
      text: INITIAL_ASSISTANT_TEXT,
    },
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [restoreReady, setRestoreReady] = useState(false)
  const [selectedCarId, setSelectedCarId] = useState<number | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const inputRef  = useRef<HTMLTextAreaElement>(null)
  const latestRankTopIdRef = useRef('')
  const autoSentQueryRef = useRef('')
  const nextMessageIdRef = useRef(2)
  const hasLoadedPersistedRef = useRef(false)

  const persistSnapshot = (snapshotMessages: Message[], snapshotInput: string) => {
    const persistedMessages = snapshotMessages
      .filter((m): m is Exclude<Message, { role: 'loading'; id: number }> => m.role !== 'loading')
      .map(m => (m.role === 'assistant'
        ? { id: m.id, role: m.role, text: m.text, cars: m.cars }
        : { id: m.id, role: m.role, text: m.text }))
    try {
      sessionStorage.setItem(
        RECOMMENDATION_CHAT_STORAGE_KEY,
        JSON.stringify({ messages: persistedMessages, input: snapshotInput }),
      )
      localStorage.setItem(
        RECOMMENDATION_CHAT_LOCAL_STORAGE_KEY,
        JSON.stringify({ messages: persistedMessages, input: snapshotInput }),
      )
    } catch {
      // ignore persist failure
    }
  }

  useEffect(() => {
    try {
      const raw = sessionStorage.getItem(RECOMMENDATION_CHAT_STORAGE_KEY)
        ?? localStorage.getItem(RECOMMENDATION_CHAT_LOCAL_STORAGE_KEY)
      if (raw) {
        const parsed = JSON.parse(raw) as { messages?: Message[]; input?: string }
        const restored = Array.isArray(parsed?.messages)
          ? parsed.messages
              .filter((m): m is Exclude<Message, { role: 'loading'; id: number }> =>
                !!m && (m.role === 'user' || m.role === 'assistant') && typeof m.id === 'number'
              )
              .map(m => ({ ...m, animate: false }))
          : []
        if (restored.length > 0) {
          setMessages(restored)
          const maxId = restored.reduce((max, m) => Math.max(max, m.id), 1)
          nextMessageIdRef.current = maxId + 1
        }
        if (typeof parsed?.input === 'string') {
          setInput(parsed.input)
        }
      }
    } catch {
      // ignore restore failure
    } finally {
      hasLoadedPersistedRef.current = true
      setRestoreReady(true)
    }
  }, [])

  useEffect(() => {
    if (!hasLoadedPersistedRef.current) return
    persistSnapshot(messages, input)
  }, [messages, input])

  useEffect(() => {
    const latestAssistantWithCars = [...messages]
      .reverse()
      .find((m): m is Extract<Message, { role: 'assistant' }> => m.role === 'assistant' && !!m.cars && m.cars.length > 0)

    if (latestAssistantWithCars) {
      const rankTopId = `assistant-${latestAssistantWithCars.id}-rank-1`
      if (latestRankTopIdRef.current !== rankTopId) {
        latestRankTopIdRef.current = rankTopId
        const el = document.getElementById(rankTopId)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' })
          return
        }
      }
    }

    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async (text?: string) => {
    const query = (text ?? input).trim()
    if (!query || loading) return
    setInput('')

    const userId = nextMessageIdRef.current++
    const loadingId = nextMessageIdRef.current++
    setMessages(prev => [...prev, { id: userId, role: 'user', text: query }, { id: loadingId, role: 'loading' }])
    setLoading(true)

    try {
      const res: RecommendationResponse = await getRecommendations({ query, maxResults: 5, useLlm: true })
      const assistantId = nextMessageIdRef.current++
      setMessages(prev => [
        ...prev.filter(m => m.role !== 'loading'),
        { id: assistantId, role: 'assistant', text: res.recommendation ?? '추천 결과를 확인해보세요.', cars: res.cars ?? [], animate: true },
      ])
    } catch {
      const assistantId = nextMessageIdRef.current++
      setMessages(prev => [
        ...prev.filter(m => m.role !== 'loading'),
        { id: assistantId, role: 'assistant', text: '죄송합니다. AI 추천 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.', animate: true },
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
    if (!restoreReady) return
    if (!queryParam) {
      autoSentQueryRef.current = ''
      return
    }
    if (loading) return
    if (autoSentQueryRef.current === queryParam) return
    autoSentQueryRef.current = queryParam
    handleSend(queryParam)
    // query 파라미터는 1회 소비 후 제거 (상세 → 복귀 시 오래된 query 재실행 방지)
    navigate('/recommendation', { replace: true })
  }, [queryParam, loading, restoreReady, navigate])

  return (
    <>
      <div className="max-w-3xl mx-auto animate-fade-in pb-[225px] sm:pb-[250px]">
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

        {/* 차량 상세 슬라이드업 시트 */}
        <CarDetailSheet carId={selectedCarId} onClose={() => setSelectedCarId(null)} />

        {/* 메시지 영역 (페이지 전체 스크롤 사용) */}
        <div className="card p-3 sm:p-5 space-y-4 sm:space-y-5">
          {/* 빠른 프롬프트 (초기 상태) */}
          {messages.length === 1 && (
            <div className="space-y-2">
              <p className="text-xs text-gray-400 font-medium">빠른 질문</p>
              <div className="flex flex-wrap gap-1.5 sm:gap-2">
                {QUICK_PROMPTS.map(p => (
                  <button
                    key={p}
                    onClick={() => handleSend(p)}
                    className="text-xs sm:text-sm px-2.5 sm:px-3 py-1.5 rounded-full bg-brand-50 text-brand-700 border border-brand-100 hover:bg-brand-100 transition-colors font-medium"
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg, i) => {
            if (msg.role === 'loading') return (
              <LoadingBubble key={msg.id} />
            )

            if (msg.role === 'user') return (
              <div key={msg.id} className="flex justify-end gap-2 sm:gap-3">
                <div className="max-w-[85%] sm:max-w-[80%] bg-brand-600 text-white rounded-2xl rounded-tr-sm px-3 sm:px-4 py-2.5 sm:py-3 text-sm leading-relaxed">
                  {msg.text}
                </div>
                <div className="w-7 h-7 sm:w-8 sm:h-8 bg-gray-200 rounded-full flex items-center justify-center text-sm shrink-0">👤</div>
              </div>
            )

            // assistant
            return (
              <div key={msg.id} className="flex gap-2 sm:gap-3">
                <div className="w-7 h-7 sm:w-8 sm:h-8 bg-brand-100 rounded-full flex items-center justify-center text-sm shrink-0">🤖</div>
                <div className="flex-1 space-y-2 sm:space-y-3 max-w-[90%] sm:max-w-[85%]">
                  {/* 텍스트 */}
                  <div className="bg-gray-50 border border-gray-100 rounded-2xl rounded-tl-sm px-3 sm:px-4 py-2.5 sm:py-3 text-sm leading-relaxed whitespace-pre-wrap text-gray-700">
                    <TypewriterText text={msg.text} animate={!!msg.animate} />
                  </div>

                  {/* 추천 차량 카드 */}
                  {msg.cars && msg.cars.length > 0 && (
                    <div className="space-y-2 sm:space-y-3">
                      {msg.cars
                        .filter((car, i, arr) => arr.findIndex(c => c.carId === car.carId) === i)
                        .map((car, ci) => (
                          <RecommendedCarCard
                            key={car.carId ?? ci}
                            car={car}
                            rank={ci + 1}
                            anchorId={ci === 0 ? `assistant-${msg.id}-rank-1` : undefined}
                            onOpenDetail={(carId) => setSelectedCarId(carId)}
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
      </div>

      {/* 하단 고정 광고 + 입력창 */}
      <div className="fixed inset-x-0 bottom-0 z-[60] pointer-events-none">
        <div className="max-w-3xl mx-auto px-2 sm:px-0 pb-[calc(env(safe-area-inset-bottom)+8px)] pointer-events-auto">
          <div className="border border-gray-100 bg-white/95 supports-[backdrop-filter]:bg-white/90 backdrop-blur rounded-t-2xl sm:rounded-2xl shadow-[0_-10px_30px_rgba(15,23,42,0.15)]">
            <div className="px-3 sm:px-5 pt-2 sm:pt-3 border-b border-gray-100">
              <AdSlot id="recommendation-banner" variant="leaderboard" />
            </div>

            <div className="p-3 sm:p-4">
              <div className="flex gap-2 items-end">
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
              <p className="text-[10px] text-gray-300 mt-1 sm:mt-1.5 text-center">
                AI 추천은 참고용이며, 실제 거래 전 반드시 직접 확인하세요
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
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
  rank,
  anchorId,
  onOpenDetail,
}: {
  car: RecommendedCar
  rank: number
  anchorId?: string
  onOpenDetail?: (carId: number) => void
}) {
  const [liked, setLiked]     = useState(false)
  const [likeCount, setCount] = useState(0)
  const [imgSrc, setImgSrc]   = useState(car.imageUrl || NO_IMAGE)
  const [reasonExpanded, setReasonExpanded] = useState(false)
  const cleanText = (v?: string | null) => {
    if (!v) return ''
    const t = v.trim()
    if (!t) return ''
    if (t.toLowerCase() === 'null') return ''
    return t
  }
  const makerText = cleanText(car.maker)
  const modelText = cleanText(car.model)
  const trimText = cleanText(car.trim)
  const titleText = [makerText, modelText].filter(Boolean).join(' ').trim() || '차량 정보'

  useEffect(() => {
    if (!car.carId) return
    getLike(car.carId).then(r => { setLiked(r.liked); setCount(r.count) }).catch(() => {})
  }, [car.carId])

  const handleLike = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!car.carId) return
    const res = await toggleLike(car.carId).catch(() => null)
    if (res) { setLiked(res.liked); setCount(res.count) }
  }

  const openDetail = () => {
    if (!car.carId) return
    onOpenDetail?.(car.carId)
  }

  const cfg = RANK_STYLES[rank]

  // ── 1~3위: 메달 헤더 강조 카드 ──────────────────────────────────────────────
  if (cfg) {
    return (
      <div
        id={anchorId}
        className={`rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition-shadow cursor-pointer ${cfg.border} ${cfg.cardBg}`}
        onClick={openDetail}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            openDetail()
          }
        }}
      >
        {/* 순위 헤더 배너 */}
        <div className={`${cfg.headerBg} px-3 py-1.5 flex items-center gap-2`}>
          <span className="text-base leading-none">{cfg.medal}</span>
          <span className="text-white text-xs font-black tracking-wide">{cfg.label}</span>
          {cfg.badge && (
            <span className="ml-auto text-[10px] font-bold bg-white/20 text-white px-2 py-0.5 rounded-full">
              {cfg.badge}
            </span>
          )}
        </div>

        {/* 카드 바디 */}
        <div className="p-3 sm:p-4 flex gap-3">
          {/* 이미지 */}
          <div className={`${cfg.imgClass} rounded-xl overflow-hidden bg-gray-50 shrink-0`}>
            <img src={imgSrc} alt={titleText}
              className="w-full h-full object-cover object-[center_65%]"
              onError={() => setImgSrc(NO_IMAGE)} />
          </div>

          {/* 정보 */}
          <div className="flex-1 min-w-0">
            <div className={`${cfg.nameClass} truncate`}>
              {titleText}
              {trimText && <span className="font-normal text-gray-400 ml-1 text-[10px] sm:text-xs">{trimText}</span>}
            </div>
            <div className="flex flex-wrap gap-1 mt-1">
              {car.year    && <span className="badge badge-gray text-[10px]">{car.year}년</span>}
              {car.mileage && <span className="badge badge-gray text-[10px]">{car.mileage.toLocaleString()}km</span>}
              {car.fuel    && <span className="badge badge-blue text-[10px]">{car.fuel}</span>}
            </div>
            {car.reason && (
              <div className="mt-1">
                <p className={`text-[10px] sm:text-xs text-gray-500 ${reasonExpanded ? '' : 'line-clamp-2'}`}>{car.reason}</p>
                {car.reason.length > 55 && (
                  <button
                    type="button"
                    className="text-[10px] text-brand-600 mt-0.5 hover:underline"
                    onClick={(e) => {
                      e.preventDefault()
                      e.stopPropagation()
                      setReasonExpanded(v => !v)
                    }}
                  >
                    {reasonExpanded ? '접기' : '더보기'}
                  </button>
                )}
              </div>
            )}
            {car.price && (
              <div className={`${cfg.priceClass} mt-1`}>{car.price.toLocaleString()}만원</div>
            )}
          </div>

          {/* 액션 */}
          <div className="flex flex-col items-end gap-1.5 shrink-0">
            {car.carId && (
              <button onClick={handleLike}
                className={`flex items-center gap-0.5 text-[10px] sm:text-xs font-semibold px-2 py-1 rounded-lg transition-colors
                  ${liked ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-400 hover:bg-red-50'}`}>
                <svg className="w-3 h-3 sm:w-3.5 sm:h-3.5" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
                </svg>
                {likeCount}
              </button>
            )}
          </div>
        </div>
      </div>
    )
  }

  // ── 4위 이하: 컴팩트 카드 ──────────────────────────────────────────────────
  return (
    <div
      id={anchorId}
      className="card p-2.5 sm:p-3 flex gap-2 sm:gap-3 hover:shadow-sm transition-shadow cursor-pointer"
      onClick={openDetail}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          openDetail()
        }
      }}
    >
      {/* 순위 뱃지 */}
      <div className="w-5 h-5 sm:w-6 sm:h-6 rounded-full bg-gray-100 text-gray-500 text-[10px] font-bold flex items-center justify-center shrink-0 mt-0.5">
        {rank}
      </div>

      {/* 이미지 */}
      <div className="w-16 h-12 sm:w-20 sm:h-14 rounded-lg overflow-hidden bg-gray-50 shrink-0">
        <img src={imgSrc} alt={titleText}
          className="w-full h-full object-cover object-[center_65%]"
          onError={() => setImgSrc(NO_IMAGE)} />
      </div>

      {/* 정보 */}
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-xs sm:text-sm text-gray-900 truncate">
          {titleText}
          {trimText && <span className="font-normal text-gray-400 ml-1 text-[10px]">{trimText}</span>}
        </div>
        <div className="flex flex-wrap gap-1 mt-0.5">
          {car.year    && <span className="badge badge-gray text-[10px]">{car.year}년</span>}
          {car.mileage && <span className="badge badge-gray text-[10px]">{car.mileage.toLocaleString()}km</span>}
          {car.fuel    && <span className="badge badge-blue text-[10px]">{car.fuel}</span>}
        </div>
        {car.reason && (
          <div className="mt-0.5">
            <p className={`text-[10px] text-gray-400 ${reasonExpanded ? '' : 'line-clamp-1'}`}>{car.reason}</p>
            {car.reason.length > 38 && (
              <button
                type="button"
                className="text-[10px] text-brand-600 mt-0.5 hover:underline"
                onClick={(e) => {
                  e.preventDefault()
                  e.stopPropagation()
                  setReasonExpanded(v => !v)
                }}
              >
                {reasonExpanded ? '접기' : '더보기'}
              </button>
            )}
          </div>
        )}
        {car.price && (
          <div className="text-xs font-bold text-brand-600 mt-0.5">{car.price.toLocaleString()}만원</div>
        )}
      </div>

      {/* 액션 */}
      <div className="flex flex-col items-end gap-1 shrink-0">
        {car.carId && (
          <button onClick={handleLike}
            className={`flex items-center gap-0.5 text-[10px] font-semibold px-1.5 py-0.5 rounded-lg transition-colors
              ${liked ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-400 hover:bg-red-50'}`}>
            <svg className="w-3 h-3" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M4.318 6.318a4.5 4.5 0 016.364 0L12 7.636l1.318-1.318a4.5 4.5 0 116.364 6.364L12 20.364l-7.682-7.682a4.5 4.5 0 010-6.364z" />
            </svg>
            {likeCount}
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

// ── 차량 상세 슬라이드업 시트 ────────────────────────────────────────────────
function CarDetailSheet({ carId, onClose }: { carId: number | null; onClose: () => void }) {
  const isOpen = carId != null
  const [renderCarId, setRenderCarId] = useState<number | null>(null)
  const [dragY, setDragY] = useState(0)
  const dragStartY = useRef<number | null>(null)
  const pointerDragging = useRef(false)

  // 열릴 때 즉시, 닫힐 때 애니메이션 후 언마운트
  useEffect(() => {
    if (carId != null) {
      setRenderCarId(carId)
      setDragY(0)
    } else {
      const t = setTimeout(() => setRenderCarId(null), 300)
      return () => clearTimeout(t)
    }
  }, [carId])

  // ESC 닫기 + body 스크롤 잠금
  useEffect(() => {
    if (!isOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = prev
      document.removeEventListener('keydown', onEsc)
    }
  }, [isOpen, onClose])

  // 드래그 핸들 포인터 이벤트 (모바일 터치 + PC 마우스)
  const onHandlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    dragStartY.current = e.clientY
    pointerDragging.current = true
    e.currentTarget.setPointerCapture?.(e.pointerId)
  }
  const onHandlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!pointerDragging.current || dragStartY.current == null) return
    const dy = e.clientY - dragStartY.current
    if (dy > 0) setDragY(dy)
  }
  const onHandlePointerEnd = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!pointerDragging.current) return
    if (dragY > 100) {
      onClose()
    } else {
      setDragY(0)
    }
    e.currentTarget.releasePointerCapture?.(e.pointerId)
    pointerDragging.current = false
    dragStartY.current = null
  }

  if (!isOpen && renderCarId == null) return null

  const isDragging = dragY > 0

  return createPortal(
    <div
      className={`fixed inset-0 z-[200] transition-opacity duration-300 ${isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
    >
      {/* 딤 배경 */}
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />

      {/* 시트 */}
      <div className="absolute inset-x-0 bottom-0 flex justify-center px-0 md:px-6 lg:px-8" style={{ top: '3rem' }}>
        <div
          className="w-full md:max-w-6xl bg-white rounded-t-2xl md:rounded-2xl shadow-2xl flex flex-col"
          style={{
            transform: isOpen ? `translateY(${dragY}px)` : 'translateY(100%)',
            transition: isDragging ? 'none' : 'transform 0.3s ease',
          }}
        >
        {/* 드래그 핸들 (아래로 스와이프해서 닫기) */}
        <div
          className="flex flex-col items-center justify-center pt-3 pb-2 shrink-0 touch-none select-none cursor-grab active:cursor-grabbing"
          onPointerDown={onHandlePointerDown}
          onPointerMove={onHandlePointerMove}
          onPointerUp={onHandlePointerEnd}
          onPointerCancel={onHandlePointerEnd}
        >
          <div className={`w-10 h-1 rounded-full transition-colors ${isDragging ? 'bg-gray-400' : 'bg-gray-300'}`} />
          {isDragging && dragY > 40 && (
            <p className="text-[10px] text-gray-400 mt-1.5">놓으면 닫힘</p>
          )}
        </div>

        {/* 헤더 */}
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-gray-100 shrink-0">
          <span className="text-sm font-bold text-gray-800">차량 상세</span>
          <button
            onClick={onClose}
            aria-label="닫기"
            className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
          >
            <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 스크롤 콘텐츠 */}
        <div
          className="flex-1 overflow-y-auto"
          onClickCapture={(e) => {
            const target = e.target as HTMLElement | null
            const anchor = target?.closest('a[href]') as HTMLAnchorElement | null
            if (!anchor) return
            const href = anchor.getAttribute('href') || ''
            if (href.startsWith('/recommendation')) {
              onClose()
            }
          }}
        >
          <div className="max-w-4xl mx-auto px-4 sm:px-6 py-4 sm:py-6">
            {renderCarId != null && (
              <CarDetail carId={renderCarId} onClose={onClose} />
            )}
          </div>
        </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}

function TypewriterText({ text, animate }: { text: string; animate: boolean }) {
  const [displayText, setDisplayText] = useState(animate ? '' : text)

  useEffect(() => {
    if (!animate) {
      setDisplayText(text)
      return
    }
    let index = 0
    setDisplayText('')
    const timer = window.setInterval(() => {
      index += 1
      if (index >= text.length) {
        setDisplayText(text)
        window.clearInterval(timer)
        return
      }
      setDisplayText(text.slice(0, index))
    }, 16)
    return () => window.clearInterval(timer)
  }, [text, animate])

  return (
    <>
      {displayText}
      {animate && displayText.length < text.length && <span className="text-brand-500 animate-pulse">▍</span>}
    </>
  )
}
