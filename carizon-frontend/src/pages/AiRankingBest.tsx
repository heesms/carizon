import { createPortal } from 'react-dom'
import { useEffect, useMemo, useState } from 'react'
import { getMakers, getModelGroups, getModels, getAiRankingBest, type AiRankingResult, type CodeItem } from '@/api/index'

// ── 유틸 ─────────────────────────────────────────────────────────────────────
const hashString = (s: string) => {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h << 5) - h + s.charCodeAt(i)
  return Math.abs(h)
}
const MAKER_GRADIENTS: Array<[string, string]> = [
  ['#0ea5e9', '#2563eb'], ['#8b5cf6', '#6d28d9'], ['#ef4444', '#dc2626'],
  ['#10b981', '#059669'], ['#f59e0b', '#d97706'], ['#64748b', '#475569'],
]
const makeFallbackSvg = (label: string) => {
  const name = (label || '?').trim()
  const letter = (name[0] || '?').toUpperCase()
  const [from, to] = MAKER_GRADIENTS[hashString(name) % MAKER_GRADIENTS.length]
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop offset="0%" stop-color="${from}"/><stop offset="100%" stop-color="${to}"/></linearGradient></defs><rect width="64" height="64" rx="14" fill="url(#g)"/><text x="50%" y="54%" text-anchor="middle" font-family="Arial, sans-serif" font-size="30" font-weight="700" fill="white">${letter}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}
const isDomesticMaker = (m: CodeItem) => {
  const raw = String((m as any).countryCode ?? '').trim().toUpperCase()
  return (m as any).domestic === 1 || (m as any).domestic === true || raw === '국산' || raw === 'KR' || raw === 'KOR' || raw === 'KO'
}
const countOf = (item: CodeItem) => Number((item as any).carCount ?? 0)
/** AI 랭킹 BEST 분석 가능 최소 매물 수 */
const MIN_CAR_COUNT_FOR_RANKING = 50
const sortByCount = (a: CodeItem, b: CodeItem) => {
  const diff = countOf(b) - countOf(a)
  return diff !== 0 ? diff : a.name.localeCompare(b.name, 'ko')
}

// ── 공용 컴포넌트 ─────────────────────────────────────────────────────────────
function MakerLogo({ code, name }: { code: string; name: string }) {
  const local = `/image/maker/maker${code}.png`
  const remote = `https://img.kbchachacha.com/IMG/statics/maker/o/maker${code}.png`
  const fallback = makeFallbackSvg(name)
  const [src, setSrc] = useState(local)
  useEffect(() => { setSrc(local) }, [code])
  return (
    <img src={src} alt={name}
      className="w-10 h-10 rounded-lg object-contain bg-white border border-gray-200 p-1 shrink-0"
      onError={() => { if (src !== remote) setSrc(remote); else if (src !== fallback) setSrc(fallback) }}
    />
  )
}

function ModelLogo({ code, name }: { code: string; name: string }) {
  const local = `/image/model/${code}.png`
  const remote = `https://img.kbchachacha.com/IMG/statics/carimg/${code}.png`
  const fallback = makeFallbackSvg(name)
  const [src, setSrc] = useState(local)
  useEffect(() => { setSrc(local) }, [code])
  return (
    <img src={src} alt={name}
      className="w-9 h-9 rounded-lg object-contain bg-white border border-gray-200 p-1 shrink-0"
      onError={() => { if (src !== remote) setSrc(remote); else if (src !== fallback) setSrc(fallback) }}
    />
  )
}

function ModalHeader({ title, onBack, onClose }: { title: string; onBack: () => void; onClose: () => void }) {
  return (
    <div className="shrink-0 px-4 sm:px-5 py-3 border-b border-gray-100 bg-white flex items-center justify-between gap-2">
      <button className="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 transition"
        onClick={onBack} aria-label="뒤로가기">
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
      </button>
      <h3 className="font-bold text-gray-900">{title}</h3>
      <button className="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 transition"
        onClick={onClose} aria-label="닫기">
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  )
}

function ModalPortal({ children }: { children: React.ReactNode }) {
  if (typeof document === 'undefined') return null
  return createPortal(children, document.body)
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────────
export default function AiRankingBest() {
  // picker 상태
  const [pickerOpen, setPickerOpen] = useState(false)
  const [pickerStep, setPickerStep] = useState<'maker' | 'model'>('maker')
  const [pickerSearch, setPickerSearch] = useState('')
  const [makers, setMakers] = useState<CodeItem[]>([])
  const [makersLoading, setMakersLoading] = useState(false)
  const [pickerMakerCode, setPickerMakerCode] = useState('')
  const [modelGroups, setModelGroups] = useState<CodeItem[]>([])
  const [mgLoading, setMgLoading] = useState(false)
  const [openGroupCodes, setOpenGroupCodes] = useState<string[]>([])
  const [modelsByGroup, setModelsByGroup] = useState<Record<string, CodeItem[]>>({})
  const [modelsLoadingByGroup, setModelsLoadingByGroup] = useState<Record<string, boolean>>({})

  // 선택 결과
  const [selectedMaker, setSelectedMaker] = useState<CodeItem | null>(null)
  const [selectedModel, setSelectedModel] = useState<CodeItem | null>(null)
  const [selectedModelGroupCode, setSelectedModelGroupCode] = useState('')

  // AI 실행 상태
  const [runStep, setRunStep] = useState<'idle' | 'loading' | 'done' | 'error'>('idle')
  const [result, setResult] = useState<AiRankingResult | null>(null)
  const [errorMsg, setErrorMsg] = useState('')

  // picker ESC 닫기 + body scroll lock
  useEffect(() => {
    if (!pickerOpen) return
    const prev = document.body.style.overflow
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setPickerOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', onEsc) }
  }, [pickerOpen])

  // 제조사 로드
  const loadMakers = () => {
    if (makersLoading) return
    setMakersLoading(true)
    getMakers().then(setMakers).catch(() => setMakers([])).finally(() => setMakersLoading(false))
  }

  // 제조사 선택 → 모델그룹 로드
  useEffect(() => {
    if (!pickerMakerCode || !pickerOpen) return
    setMgLoading(true)
    getModelGroups(pickerMakerCode)
      .then(setModelGroups)
      .catch(() => setModelGroups([]))
      .finally(() => setMgLoading(false))
  }, [pickerMakerCode, pickerOpen])

  // 그룹 열릴 때 모델 로드
  const ensureGroupModels = (groupCode: string) => {
    if (!pickerMakerCode || modelsByGroup[groupCode] || modelsLoadingByGroup[groupCode]) return
    setModelsLoadingByGroup(prev => ({ ...prev, [groupCode]: true }))
    getModels(pickerMakerCode, groupCode)
      .then(data => setModelsByGroup(prev => ({ ...prev, [groupCode]: data })))
      .catch(() => setModelsByGroup(prev => ({ ...prev, [groupCode]: [] })))
      .finally(() => setModelsLoadingByGroup(prev => ({ ...prev, [groupCode]: false })))
  }

  useEffect(() => {
    if (!pickerOpen || pickerStep !== 'model') return
    openGroupCodes.forEach(ensureGroupModels)
  }, [pickerOpen, pickerStep, pickerMakerCode, openGroupCodes])

  const openPicker = () => {
    if (makers.length === 0) loadMakers()
    setPickerSearch('')
    setPickerMakerCode(selectedMaker?.code ?? '')
    setOpenGroupCodes(selectedModelGroupCode ? [selectedModelGroupCode] : [])
    setModelGroups([])
    setModelsByGroup({})
    setModelsLoadingByGroup({})
    setPickerStep(selectedMaker ? 'model' : 'maker')
    setPickerOpen(true)
  }

  const chooseMaker = (maker: CodeItem) => {
    if (countOf(maker) <= 0) return
    setPickerMakerCode(maker.code)
    setOpenGroupCodes([])
    setModelGroups([])
    setModelsByGroup({})
    setModelsLoadingByGroup({})
    setPickerSearch('')
    setPickerStep('model')
  }

  const chooseModel = (maker: CodeItem, groupCode: string, model: CodeItem) => {
    setSelectedMaker(maker)
    setSelectedModel(model)
    setSelectedModelGroupCode(groupCode)
    setResult(null)
    setRunStep('idle')
    setPickerOpen(false)
  }

  const handleRun = async () => {
    if (!selectedModel) return
    setRunStep('loading')
    setResult(null)
    setErrorMsg('')
    try {
      const data = await getAiRankingBest(selectedModel.code)
      setResult(data)
      setRunStep('done')
    } catch (e: unknown) {
      setErrorMsg(e instanceof Error ? e.message : '알 수 없는 오류')
      setRunStep('error')
    }
  }

  // picker 검색 필터
  const keyword = pickerSearch.trim().toLowerCase()
  const match = (item: CodeItem) => !keyword || item.name.toLowerCase().includes(keyword) || String(item.code).toLowerCase().includes(keyword)

  const domesticMakers = useMemo(() => makers.filter(isDomesticMaker).sort(sortByCount), [makers])
  const importedMakers = useMemo(() => makers.filter(m => !isDomesticMaker(m)).sort(sortByCount), [makers])
  const visibleDomestic = domesticMakers.filter(match)
  const visibleImported = importedMakers.filter(match)

  const popularGroups = useMemo(
    () => modelGroups.filter(g => countOf(g) > 0).sort(sortByCount).slice(0, 10),
    [modelGroups]
  )
  const nameGroups = useMemo(
    () => [...modelGroups].sort((a, b) => a.name.localeCompare(b.name, 'ko')),
    [modelGroups]
  )
  const visibleByGroup = (groupCode: string) =>
    (modelsByGroup[groupCode] ?? []).filter(match).sort(sortByCount)

  const activeMaker = makers.find(m => m.code === pickerMakerCode)

  return (
    <div className="max-w-4xl mx-auto animate-fade-in">
      {/* 헤더 */}
      <div className="mb-6">
        <div className="flex items-center gap-3 mb-1">
          <div className="w-9 h-9 sm:w-10 sm:h-10 bg-brand-600 rounded-xl flex items-center justify-center text-lg sm:text-xl">🏆</div>
          <div>
            <h1 className="text-lg sm:text-xl font-black text-gray-900">AI 랭킹 BEST</h1>
            <p className="text-[11px] sm:text-xs text-gray-400">모델을 선택하면 AI가 최고의 매물을 자동 분석합니다</p>
          </div>
        </div>
      </div>

      {/* 선택 카드 */}
      <div className="card p-4 sm:p-6 mb-6">
        <p className="text-xs font-semibold text-gray-500 mb-3">차량 선택</p>

        <button
          className="w-full sm:w-auto text-left px-4 py-3 rounded-xl border border-gray-200 bg-white hover:border-brand-300 transition flex items-center gap-3"
          onClick={openPicker}
        >
          {selectedMaker ? (
            <>
              <MakerLogo code={selectedMaker.code} name={selectedMaker.name} />
              <div className="min-w-0">
                <p className="text-xs text-gray-400">{selectedMaker.name}</p>
                <p className="font-bold text-gray-900 truncate">{selectedModel?.name ?? '모델 선택'}</p>
              </div>
              <span className="text-xs text-gray-400 ml-auto">▼</span>
            </>
          ) : (
            <>
              <div className="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center text-gray-400 shrink-0">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
              </div>
              <span className="text-gray-500 text-sm">제조사 / 모델 선택</span>
              <span className="text-xs text-gray-400 ml-auto">▼</span>
            </>
          )}
        </button>

        {selectedModel && (
          <div className="mt-4">
            <button
              onClick={handleRun}
              disabled={runStep === 'loading'}
              className="btn-primary px-6 py-2.5 text-sm font-bold disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {runStep === 'loading' ? (
                <>
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                  </svg>
                  AI 분석 중...
                </>
              ) : (
                <>
                  <span>🏆</span>
                  AI 랭킹 BEST 분석
                </>
              )}
            </button>
          </div>
        )}
      </div>

      {/* 로딩 */}
      {runStep === 'loading' && (
        <div className="card p-8 text-center">
          <div className="flex justify-center gap-1.5 mb-3">
            {[0, 1, 2].map(d => (
              <div key={d} className="w-2.5 h-2.5 bg-brand-400 rounded-full animate-bounce"
                style={{ animationDelay: `${d * 0.15}s` }} />
            ))}
          </div>
          <p className="text-sm text-gray-500">AI가 최고의 매물을 분석하고 있어요...</p>
        </div>
      )}

      {/* 오류 */}
      {runStep === 'error' && (
        <div className="card p-5 border-red-100 bg-red-50">
          <p className="text-sm font-semibold text-red-600 mb-1">분석 실패</p>
          <p className="text-xs text-red-500">{errorMsg}</p>
        </div>
      )}

      {/* 결과 */}
      {runStep === 'done' && result && (
        <div className="card overflow-hidden shadow-lg">
          {/* 헤더 */}
          <div className="bg-gradient-to-r from-brand-600 to-brand-700 px-5 py-4 flex items-center gap-3 justify-between">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-9 h-9 bg-white/20 rounded-xl flex items-center justify-center text-xl shrink-0">🏆</div>
              <div className="min-w-0">
                <p className="text-white font-black text-sm truncate">{result.title}</p>
                {result.carCount != null && (
                  <p className="text-blue-100 text-[11px] mt-0.5">분석 완료 · 매물 {result.carCount}개 선별</p>
                )}
              </div>
            </div>
            <button
              onClick={() => { setResult(null); setRunStep('idle') }}
              className="w-7 h-7 rounded-lg bg-white/10 hover:bg-white/25 flex items-center justify-center text-white/70 hover:text-white transition shrink-0"
              aria-label="닫기"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* 순위 HTML 스타일 */}
          <style>{`
            .ranking-content h2 { font-size:1rem; font-weight:700; color:#1e293b; margin-top:1.25rem; margin-bottom:0.75rem; }
            .ranking-content h3 { font-size:0.95rem; font-weight:700; color:#2563eb; margin:1.25rem 0 1rem; padding-bottom:0.5rem; border-bottom:2px solid #dbeafe; }
            .ranking-content h4 { font-size:0.875rem; font-weight:700; margin-bottom:12px; border-radius:8px; padding:6px 10px; background:#f1f5f9; color:#334155; }
            .ranking-content h4 a { color:inherit; text-decoration:none; }
            .ranking-content h4 a:hover { text-decoration:underline; }
            .ranking-content ul { list-style:none !important; padding-left:0 !important; margin:0; }
            .ranking-content ul > li { border:1px solid #e2e8f0; border-radius:12px; padding:16px; margin-bottom:16px; background:#fff; box-shadow:0 1px 3px rgba(0,0,0,0.05); transition:box-shadow 0.15s; }
            .ranking-content ul > li:hover { box-shadow:0 4px 12px rgba(0,0,0,0.1); }
            .ranking-content ul > li:nth-child(1) { background:linear-gradient(150deg,#fef3c7 0%,#fffbeb 70%); border:2px solid #fbbf24 !important; }
            .ranking-content ul > li:nth-child(1) h4 { background:linear-gradient(135deg,#fbbf24,#f59e0b); color:#fff; font-weight:900; font-size:1rem; }
            .ranking-content ul > li:nth-child(1) h4 a { color:#fff !important; }
            .ranking-content ul > li:nth-child(2) { background:linear-gradient(150deg,#f1f5f9 0%,#f8fafc 70%); border:2px solid #94a3b8 !important; }
            .ranking-content ul > li:nth-child(2) h4 { background:linear-gradient(135deg,#94a3b8,#64748b); color:#fff; font-weight:800; }
            .ranking-content ul > li:nth-child(2) h4 a { color:#fff !important; }
            .ranking-content ul > li:nth-child(3) { background:linear-gradient(150deg,#fff7ed 0%,#fffbf5 70%); border:1.5px solid #fb923c !important; }
            .ranking-content ul > li:nth-child(3) h4 { background:linear-gradient(135deg,#fb923c,#f97316); color:#fff; font-weight:800; }
            .ranking-content ul > li:nth-child(3) h4 a { color:#fff !important; }
            .ranking-content table { font-size:0.8rem; }
            .ranking-content table td { padding:6px 8px !important; }
            .ranking-content table tr:nth-child(even) td { background-color:#f9fafb; }
            .ranking-content p a { color:#2563eb; font-weight:600; }
            .ranking-content p a:hover { text-decoration:underline; }
          `}</style>

          {/* 랭킹 HTML 콘텐츠 */}
          <div
            className="ranking-content p-5 prose prose-sm max-w-none"
            dangerouslySetInnerHTML={{ __html: result.content }}
          />
        </div>
      )}

      {/* ── 모달 ── */}
      {pickerOpen && (
        <ModalPortal>
          <div className="fixed inset-0 z-[220]">
            <button className="absolute inset-0 bg-black/45" onClick={() => setPickerOpen(false)} />
            <div className="absolute inset-0 md:p-6 md:flex md:items-center md:justify-center">
              <div
                className="h-[100dvh] md:h-auto md:max-h-[88vh] w-full md:max-w-4xl bg-white md:rounded-2xl shadow-2xl overflow-hidden flex flex-col"
                style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
              >
                <ModalHeader
                  title={pickerStep === 'maker' ? '제조사 선택' : '모델 선택'}
                  onBack={() => {
                    if (pickerStep === 'model') { setPickerStep('maker'); setPickerSearch('') }
                    else setPickerOpen(false)
                  }}
                  onClose={() => setPickerOpen(false)}
                />

                {/* 검색 */}
                <div className="px-4 sm:px-5 py-3 border-b border-gray-100 bg-white">
                  <div className="relative">
                    <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    <input
                      className="input text-sm pl-9"
                      placeholder={pickerStep === 'maker' ? '제조사 검색' : '모델그룹/모델 검색'}
                      value={pickerSearch}
                      onChange={e => setPickerSearch(e.target.value)}
                    />
                  </div>
                </div>

                <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-5 bg-gray-50">

                  {/* ── 제조사 step ── */}
                  {pickerStep === 'maker' && (
                    <>
                      {makersLoading && (
                        <div className="text-center text-sm text-gray-500 py-10">제조사 목록 불러오는 중...</div>
                      )}
                      {!makersLoading && (
                        <>
                          <div>
                            <p className="text-xs font-semibold text-gray-600 mb-2">국산</p>
                            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                              {visibleDomestic.map(m => (
                                <button
                                  key={m.code}
                                  disabled={countOf(m) <= 0}
                                  className={`flex items-center gap-2 px-2.5 py-2 rounded-xl border text-left transition
                                    ${pickerMakerCode === m.code ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:bg-gray-50'}
                                    ${countOf(m) <= 0 ? 'opacity-35 cursor-not-allowed' : ''}`}
                                  onClick={() => chooseMaker(m)}
                                >
                                  <MakerLogo code={m.code} name={m.name} />
                                  <span className="text-xs font-semibold truncate">{m.name}</span>
                                </button>
                              ))}
                            </div>
                          </div>
                          <div>
                            <p className="text-xs font-semibold text-gray-600 mb-2">외산</p>
                            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                              {visibleImported.map(m => (
                                <button
                                  key={m.code}
                                  disabled={countOf(m) <= 0}
                                  className={`flex items-center gap-2 px-2.5 py-2 rounded-xl border text-left transition
                                    ${pickerMakerCode === m.code ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:bg-gray-50'}
                                    ${countOf(m) <= 0 ? 'opacity-35 cursor-not-allowed' : ''}`}
                                  onClick={() => chooseMaker(m)}
                                >
                                  <MakerLogo code={m.code} name={m.name} />
                                  <span className="text-xs font-semibold truncate">{m.name}</span>
                                </button>
                              ))}
                            </div>
                          </div>
                          {visibleDomestic.length === 0 && visibleImported.length === 0 && (
                            <div className="text-center text-sm text-gray-500 py-10">검색 결과가 없습니다.</div>
                          )}
                        </>
                      )}
                    </>
                  )}

                  {/* ── 모델 step ── */}
                  {pickerStep === 'model' && (
                    <>
                      {activeMaker && (
                        <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
                          <MakerLogo code={activeMaker.code} name={activeMaker.name} />
                          <span>{activeMaker.name}</span>
                        </div>
                      )}

                      {mgLoading && (
                        <div className="text-center text-sm text-gray-500 py-10">모델 목록 불러오는 중...</div>
                      )}

                      {!mgLoading && (
                        <>
                          {/* 인기순 */}
                          <div>
                            <p className="text-xs font-semibold text-gray-500 mb-2">인기모델</p>
                            <GroupList
                              groups={popularGroups.filter(match)}
                              openGroupCodes={openGroupCodes}
                              onToggleGroup={g => {
                                const isOpen = openGroupCodes.includes(g)
                                setOpenGroupCodes(prev => isOpen ? prev.filter(c => c !== g) : [...prev, g])
                                if (!isOpen) ensureGroupModels(g)
                              }}
                              visibleByGroup={visibleByGroup}
                              loadingByGroup={modelsLoadingByGroup}
                              selectedModelCode={selectedModel?.code}
                              onSelectModel={(groupCode, model) => activeMaker && chooseModel(activeMaker, groupCode, model)}
                              prefix="popular"
                            />
                          </div>

                          {/* 이름순 */}
                          <div>
                            <p className="text-xs font-semibold text-gray-500 mb-2">이름순</p>
                            <GroupList
                              groups={nameGroups.filter(match)}
                              openGroupCodes={openGroupCodes}
                              onToggleGroup={g => {
                                const isOpen = openGroupCodes.includes(g)
                                setOpenGroupCodes(prev => isOpen ? prev.filter(c => c !== g) : [...prev, g])
                                if (!isOpen) ensureGroupModels(g)
                              }}
                              visibleByGroup={visibleByGroup}
                              loadingByGroup={modelsLoadingByGroup}
                              selectedModelCode={selectedModel?.code}
                              onSelectModel={(groupCode, model) => activeMaker && chooseModel(activeMaker, groupCode, model)}
                              prefix="name"
                            />
                          </div>
                        </>
                      )}
                    </>
                  )}
                </div>
              </div>
            </div>
          </div>
        </ModalPortal>
      )}
    </div>
  )
}

// ── 모델그룹 아코디언 리스트 ────────────────────────────────────────────────────
function GroupList({
  groups,
  openGroupCodes,
  onToggleGroup,
  visibleByGroup,
  loadingByGroup,
  selectedModelCode,
  onSelectModel,
  prefix,
}: {
  groups: CodeItem[]
  openGroupCodes: string[]
  onToggleGroup: (code: string) => void
  visibleByGroup: (code: string) => CodeItem[]
  loadingByGroup: Record<string, boolean>
  selectedModelCode?: string
  onSelectModel: (groupCode: string, model: CodeItem) => void
  prefix: string
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
      {groups.map(g => {
        const expanded = openGroupCodes.includes(g.code)
        const models = visibleByGroup(g.code)
        const loading = !!loadingByGroup[g.code]
        return (
          <div key={`${prefix}-${g.code}`} className="border-b border-gray-100 last:border-b-0">
            <button
              disabled={countOf(g) <= 0}
              className={`w-full px-3.5 py-3 text-left flex items-center justify-between transition
                ${expanded ? 'bg-brand-50' : ''} ${countOf(g) <= 0 ? 'opacity-40 cursor-not-allowed' : 'hover:bg-gray-50'}`}
              onClick={() => onToggleGroup(g.code)}
            >
              <span className={`font-semibold text-sm ${expanded ? 'text-brand-700' : 'text-gray-800'}`}>{g.name}</span>
              <div className="flex items-center gap-2">
                <span className="text-xs text-gray-400">{countOf(g).toLocaleString()}</span>
                <svg className={`w-3.5 h-3.5 text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`}
                  fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </button>

            {expanded && (
              <div className="border-t border-gray-100 bg-white">
                {loading && (
                  <div className="px-4 py-6 text-center text-xs text-gray-400">모델 목록 불러오는 중...</div>
                )}
                {!loading && models.map(m => (
                  <button
                    key={`${prefix}-model-${m.code}`}
                    className={`w-full px-4 py-2.5 border-b border-gray-100 last:border-b-0 flex items-center gap-2 transition
                      ${selectedModelCode === m.code ? 'bg-brand-50 text-brand-700' : 'bg-white text-gray-800 hover:bg-gray-50'}`}
                    onClick={() => onSelectModel(g.code, m)}
                  >
                    {/* 단일 선택 라디오 */}
                    <span className={`w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0
                      ${selectedModelCode === m.code ? 'border-brand-600 bg-brand-600' : 'border-gray-300 bg-white'}`}>
                      {selectedModelCode === m.code && <span className="w-1.5 h-1.5 rounded-full bg-white" />}
                    </span>
                    <ModelLogo code={m.code} name={m.name} />
                    <span className="text-sm font-medium text-left flex-1">{m.name}</span>
                    <span className="text-xs text-gray-400">{countOf(m).toLocaleString()}</span>
                  </button>
                ))}
                {!loading && models.length === 0 && (
                  <div className="px-4 py-6 text-center text-xs text-gray-400">모델이 없습니다.</div>
                )}
              </div>
            )}
          </div>
        )
      })}
      {groups.length === 0 && (
        <div className="px-4 py-10 text-center text-sm text-gray-500">검색 결과가 없습니다.</div>
      )}
    </div>
  )
}
