import React, { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { getBodyTypes, getMakers, getModelGroups, getModels, getTrims, type CodeItem } from '@/api/codes'

type Filters = Record<string, string | number | undefined>

type Props = {
  value: Filters
  onChange: (f: Filters) => void
  onSearch: () => void
}

type SearchMode = 'structured' | 'text'

const BODY_TYPE_OPTIONS = [
  '', '경차', '소형', '준중형', '중형', '대형', 'SUV', 'RV', '승합', '스포츠카', '트럭', '화물', '상용', '기타',
]

const FUEL_OPTIONS = ['가솔린', '디젤', '하이브리드', '전기', 'LPG']
const CURRENT_YEAR = new Date().getFullYear()
const YEAR_MIN_BOUND = 1990
const YEAR_MAX_BOUND = Math.max(2027, CURRENT_YEAR + 1)
const PRICE_MIN_BOUND = 0
const PRICE_MAX_BOUND = 20000
const KM_MIN_BOUND = 0
const KM_MAX_BOUND = 300000
const YEAR_HANDLE_MAX = YEAR_MAX_BOUND - YEAR_MIN_BOUND + 1
const YEAR_END_HANDLE_MAX = YEAR_HANDLE_MAX + 1
const PRICE_MAX_UNLIMITED = PRICE_MAX_BOUND + 100
const YEAR_MAX_UNLIMITED = YEAR_MAX_BOUND + 1
const KM_MAX_UNLIMITED = KM_MAX_BOUND + 1000

const MAKER_GRADIENTS: Array<[string, string]> = [
  ['#0ea5e9', '#2563eb'],
  ['#8b5cf6', '#6d28d9'],
  ['#ef4444', '#dc2626'],
  ['#10b981', '#059669'],
  ['#f59e0b', '#d97706'],
  ['#64748b', '#475569'],
]

const hashString = (s: string) => {
  let h = 0
  for (let i = 0; i < s.length; i += 1) h = (h << 5) - h + s.charCodeAt(i)
  return Math.abs(h)
}

const makeFallbackSvg = (label: string) => {
  const name = (label || '?').trim()
  const letter = (name[0] || '?').toUpperCase()
  const [from, to] = MAKER_GRADIENTS[hashString(name) % MAKER_GRADIENTS.length]
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop offset="0%" stop-color="${from}"/><stop offset="100%" stop-color="${to}"/></linearGradient></defs><rect width="64" height="64" rx="14" fill="url(#g)"/><text x="50%" y="54%" text-anchor="middle" font-family="Arial, sans-serif" font-size="30" font-weight="700" fill="white">${letter}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

const isDomesticMaker = (maker: CodeItem) => {
  if (maker.domestic === 1 || maker.domestic === true) return true
  const raw = String(maker.countryCode ?? '').trim()
  const up = raw.toUpperCase()
  return raw === '국산' || up === 'KR' || up === 'KOR' || up === 'KO'
}

const countOf = (item: CodeItem) => Number(item.carCount ?? 0)

const sortByCountThenName = (a: CodeItem, b: CodeItem) => {
  const ac = countOf(a)
  const bc = countOf(b)
  if (bc !== ac) return bc - ac
  return a.name.localeCompare(b.name, 'ko')
}

const toNum = (v: unknown, fallback: number) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

const clamp = (n: number, min: number, max: number) => Math.min(max, Math.max(min, n))

const parseCsvTokens = (raw: unknown) =>
  String(raw ?? '')
    .split(',')
    .map(v => v.trim())
    .filter(Boolean)

const formatPriceManwonLabel = (value: number) => {
  if (value < 10000) return `${value.toLocaleString()}만원`
  const eok = Math.floor(value / 10000)
  const rest = value % 10000
  if (rest === 0) return `${eok}억`
  if (rest % 1000 === 0) return `${eok}억${rest / 1000}천만원`
  return `${eok}억${rest.toLocaleString()}만원`
}

function MakerLogo({ makerCode, makerName, className }: { makerCode: string; makerName: string; className?: string }) {
  const local = `/image/maker/maker${makerCode}.png`
  const remote = `https://img.kbchachacha.com/IMG/statics/maker/o/maker${makerCode}.png`
  const fallback = makeFallbackSvg(makerName)
  const [src, setSrc] = useState(local)

  useEffect(() => { setSrc(local) }, [makerCode])

  return (
    <img
      src={src}
      alt={makerName}
      className={className ?? "w-10 h-10 rounded-lg object-contain bg-white border border-gray-200 p-1 shrink-0"}
      onError={() => {
        if (src !== remote) setSrc(remote)
        else if (src !== fallback) setSrc(fallback)
      }}
    />
  )
}

function ModelLogo({ modelCode, modelName, className }: { modelCode: string; modelName: string; className?: string }) {
  const local = `/image/model/${modelCode}.png`
  const remote = `https://img.kbchachacha.com/IMG/statics/carimg/${modelCode}.png`
  const fallback = makeFallbackSvg(modelName)
  const [src, setSrc] = useState(local)

  useEffect(() => { setSrc(local) }, [modelCode])

  return (
    <img
      src={src}
      alt={modelName}
      className={className ?? "w-9 h-9 rounded-lg object-contain bg-white border border-gray-200 p-1 shrink-0"}
      onError={() => {
        if (src !== remote) setSrc(remote)
        else if (src !== fallback) setSrc(fallback)
      }}
    />
  )
}

function MakerCard({ maker, selected, onSelect }: { maker: CodeItem; selected: boolean; onSelect: () => void }) {
  const disabled = countOf(maker) <= 0
  return (
    <button
      disabled={disabled}
      className={`flex items-center gap-2 px-2.5 py-2 rounded-xl border text-left transition ${selected
        ? 'border-brand-500 bg-brand-50 text-brand-700'
        : 'border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:bg-gray-50'} ${disabled ? 'opacity-35 cursor-not-allowed hover:border-gray-200 hover:bg-white' : ''}`}
      onClick={onSelect}
      title={maker.name}
    >
      <MakerLogo makerCode={maker.code} makerName={maker.name} />
      <span className="min-w-0 text-xs font-semibold truncate block">{maker.name}</span>
    </button>
  )
}

function ClearIconButton({
  onClick,
  label,
  className = '',
}: {
  onClick: (e: React.MouseEvent<HTMLButtonElement>) => void
  label: string
  className?: string
}) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center w-7 h-7 rounded-lg border border-gray-200 bg-white text-gray-400 hover:text-red-500 hover:border-red-200 hover:bg-red-50 transition ${className}`}
      onClick={onClick}
      aria-label={label}
      title={label}
    >
      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
  )
}

function RangeBlock({
  label,
  trackMin,
  trackMax,
  startMin,
  startMax,
  endMin,
  endMax,
  start,
  end,
  step,
  startLabel,
  endLabel,
  unit,
  startInputValue,
  endInputValue,
  startInputMin,
  startInputMax,
  endInputMin,
  endInputMax,
  onStart,
  onEnd,
  onStartInput,
  onEndInput,
  onReset,
  canReset,
  clearLabel,
}: {
  label: string
  trackMin: number
  trackMax: number
  startMin: number
  startMax: number
  endMin: number
  endMax: number
  start: number
  end: number
  step: number
  startLabel: string
  endLabel: string
  unit: string
  startInputValue: string
  endInputValue: string
  startInputMin: number
  startInputMax: number
  endInputMin: number
  endInputMax: number
  onStart: (v: number) => void
  onEnd: (v: number) => void
  onStartInput: (raw: string) => void
  onEndInput: (raw: string) => void
  onReset: () => void
  canReset?: boolean
  clearLabel?: string
}) {
  const trackSpan = Math.max(1, trackMax - trackMin)
  const startPercent = ((Math.min(Math.max(start, trackMin), trackMax) - trackMin) / trackSpan) * 100
  const endPercent = ((Math.min(Math.max(end, trackMin), trackMax) - trackMin) / trackSpan) * 100
  const rangeLeft = Math.min(startPercent, endPercent)
  const rangeRight = 100 - Math.max(startPercent, endPercent)
  const [startDraft, setStartDraft] = useState(startInputValue)
  const [endDraft, setEndDraft] = useState(endInputValue)

  useEffect(() => { setStartDraft(startInputValue) }, [startInputValue])
  useEffect(() => { setEndDraft(endInputValue) }, [endInputValue])

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-3">
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs font-semibold text-gray-500">{label}</p>
        <div className="flex items-center gap-2">
          <p className="text-xs text-gray-700 font-semibold">
            {startLabel} ~ {endLabel}
          </p>
          {canReset && (
            <ClearIconButton
              className="w-6 h-6 rounded-md"
              onClick={e => {
                e.stopPropagation()
                onReset()
              }}
              label={clearLabel ?? `${label} 조건 해제`}
            />
          )}
        </div>
      </div>

      <div className="relative h-9 mb-2">
        <div className="absolute left-0 right-0 top-1/2 -translate-y-1/2 h-2 rounded-full bg-gray-200/90" />
        <div
          className="absolute top-1/2 -translate-y-1/2 h-2 rounded-full bg-gradient-to-r from-sky-500 to-brand-600 shadow-[0_0_0_1px_rgba(255,255,255,0.45)_inset]"
          style={{ left: `${rangeLeft}%`, right: `${rangeRight}%` }}
        />
        <input
          type="range"
          min={startMin}
          max={startMax}
          step={step}
          value={start}
          onChange={e => onStart(Number(e.target.value))}
          className="pointer-events-none absolute inset-0 h-9 w-full appearance-none bg-transparent
            [&::-webkit-slider-runnable-track]:bg-transparent [&::-moz-range-track]:bg-transparent
            [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-5 [&::-webkit-slider-thumb]:w-5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-sky-500 [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-white [&::-webkit-slider-thumb]:shadow-[0_2px_10px_rgba(14,165,233,0.45)] [&::-webkit-slider-thumb]:ring-2 [&::-webkit-slider-thumb]:ring-sky-200/80
            [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:h-5 [&::-moz-range-thumb]:w-5 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-sky-500 [&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-white [&::-moz-range-thumb]:shadow [&::-moz-range-thumb]:ring-2 [&::-moz-range-thumb]:ring-sky-200/80"
        />
        <input
          type="range"
          min={endMin}
          max={endMax}
          step={step}
          value={end}
          onChange={e => onEnd(Number(e.target.value))}
          className="pointer-events-none absolute inset-0 h-9 w-full appearance-none bg-transparent
            [&::-webkit-slider-runnable-track]:bg-transparent [&::-moz-range-track]:bg-transparent
            [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-5 [&::-webkit-slider-thumb]:w-5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-brand-600 [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-white [&::-webkit-slider-thumb]:shadow-[0_2px_10px_rgba(37,99,235,0.4)] [&::-webkit-slider-thumb]:ring-2 [&::-webkit-slider-thumb]:ring-brand-200/70
            [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:h-5 [&::-moz-range-thumb]:w-5 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-brand-600 [&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-white [&::-moz-range-thumb]:shadow [&::-moz-range-thumb]:ring-2 [&::-moz-range-thumb]:ring-brand-200/70"
        />
      </div>

      <div className="grid grid-cols-[1fr_auto_1fr] gap-2 items-center">
        <div className="relative">
          <input
            type="number"
            min={startInputMin}
            max={startInputMax}
            step={step}
            value={startDraft}
            placeholder="0"
            onChange={e => setStartDraft(e.target.value)}
            onBlur={() => onStartInput(startDraft)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                onStartInput(startDraft)
                ;(e.currentTarget as HTMLInputElement).blur()
              }
            }}
            className="input text-sm pr-9"
          />
          <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-gray-400">{unit}</span>
        </div>
        <span className="text-xs text-gray-400 font-semibold">~</span>
        <div className="relative">
          <input
            type="number"
            min={endInputMin}
            max={endInputMax}
            step={step}
            value={endDraft}
            placeholder="∞"
            onChange={e => setEndDraft(e.target.value)}
            onBlur={() => onEndInput(endDraft)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                onEndInput(endDraft)
                ;(e.currentTarget as HTMLInputElement).blur()
              }
            }}
            className="input text-sm pr-9"
          />
          <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-gray-400">{unit}</span>
        </div>
      </div>
    </div>
  )
}

function ModalHeader({
  title,
  onBack,
  onClose,
}: {
  title: string
  onBack: () => void
  onClose: () => void
}) {
  return (
    <div className="shrink-0 px-4 sm:px-5 py-3 border-b border-gray-100 bg-white flex items-center justify-between gap-2">
      <button
        className="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 transition"
        onClick={onBack}
        aria-label="뒤로가기"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
      </button>
      <h3 className="font-bold text-gray-900">{title}</h3>
      <button
        className="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 transition"
        onClick={onClose}
        aria-label="닫기"
      >
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

export default function FiltersPanel({ value, onChange, onSearch }: Props) {
  const [makers, setMakers] = useState<CodeItem[]>([])
  const [makersLoading, setMakersLoading] = useState(false)
  const [makersError, setMakersError] = useState('')
  const [modelGroups, setModelGroups] = useState<CodeItem[]>([])
  const [models, setModels] = useState<CodeItem[]>([])
  const [trims, setTrims] = useState<CodeItem[]>([])

  const [filtersCollapsed, setFiltersCollapsed] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)

  const [makerPickerOpen, setMakerPickerOpen] = useState(false)
  const [modelPickerOpen, setModelPickerOpen] = useState(false)
  const [pickerSearch, setPickerSearch] = useState('')
  const [pickerMakerCode, setPickerMakerCode] = useState('')
  const [pickerOpenGroupCodes, setPickerOpenGroupCodes] = useState<string[]>([])
  const [pickerModelGroups, setPickerModelGroups] = useState<CodeItem[]>([])
  const [pickerModelGroupsLoading, setPickerModelGroupsLoading] = useState(false)
  const [pickerModelGroupsError, setPickerModelGroupsError] = useState('')
  const [pickerModelsByGroup, setPickerModelsByGroup] = useState<Record<string, CodeItem[]>>({})
  const [pickerModelsLoadingByGroup, setPickerModelsLoadingByGroup] = useState<Record<string, boolean>>({})
  const [pickerModelsErrorByGroup, setPickerModelsErrorByGroup] = useState<Record<string, string>>({})
  const [selectedModelGroupByCode, setSelectedModelGroupByCode] = useState<Record<string, string>>({})
  const [selectedModelNameByCode, setSelectedModelNameByCode] = useState<Record<string, string>>({})

  const [trimPickerOpen, setTrimPickerOpen] = useState(false)
  const [trimSearch, setTrimSearch] = useState('')

  const [bodyTypePickerOpen, setBodyTypePickerOpen] = useState(false)
  const [bodyTypeDraftCodes, setBodyTypeDraftCodes] = useState<string[]>([])
  const [bodyTypeItems, setBodyTypeItems] = useState<CodeItem[]>([])
  const [bodyTypeLoading, setBodyTypeLoading] = useState(false)
  const [bodyTypeError, setBodyTypeError] = useState('')
  const [fuelPickerOpen, setFuelPickerOpen] = useState(false)
  const [fuelDraftCodes, setFuelDraftCodes] = useState<string[]>(parseCsvTokens(value.fuel))
  const [textQueryDraft, setTextQueryDraft] = useState(String(value.q ?? ''))
  const [searchMode, setSearchMode] = useState<SearchMode>(
    String(value.q ?? '').trim() ? 'text' : 'structured'
  )

  const makerCode = String(value.makerCode ?? '')
  const modelGroupCode = String(value.modelGroupCode ?? '')
  const modelCode = String(value.modelCode ?? '')
  const selectedModelCodes = modelCode
    .split(',')
    .map(v => v.trim())
    .filter(Boolean)
  const singleModelCode = selectedModelCodes.length === 1 ? selectedModelCodes[0] : ''
  const singleModelGroupCode = selectedModelCodes.length === 1
    ? (selectedModelGroupByCode[singleModelCode] || modelGroupCode)
    : ''
  const trimCode = String(value.trimCode ?? '')
  const bodyType = String(value.bodyType ?? '')
  const selectedBodyTypes = bodyType
    .split(',')
    .map(v => v.trim())
    .filter(Boolean)
  const hasStructuredSelection = Boolean(makerCode || modelGroupCode || modelCode || trimCode)

  const priceMinValue = clamp(toNum(value.priceMin, PRICE_MIN_BOUND), PRICE_MIN_BOUND, PRICE_MAX_BOUND)
  const priceMaxValue = value.priceMax == null || value.priceMax === ''
    ? PRICE_MAX_UNLIMITED
    : clamp(toNum(value.priceMax, PRICE_MAX_BOUND), PRICE_MIN_BOUND, PRICE_MAX_BOUND)
  const safePriceMin = Math.min(priceMinValue, priceMaxValue)
  const safePriceMax = Math.max(priceMinValue, priceMaxValue)

  const yearMinValue = value.yearMin == null || value.yearMin === ''
    ? 0
    : clamp(toNum(value.yearMin, YEAR_MIN_BOUND), YEAR_MIN_BOUND, YEAR_MAX_BOUND)
  const yearMaxValue = value.yearMax == null || value.yearMax === ''
    ? YEAR_MAX_UNLIMITED
    : clamp(toNum(value.yearMax, YEAR_MAX_BOUND), YEAR_MIN_BOUND, YEAR_MAX_BOUND)
  const safeYearMin = Math.min(yearMinValue, yearMaxValue)
  const safeYearMax = Math.max(yearMinValue, yearMaxValue)
  const yearStartHandle = safeYearMin <= 0 ? 0 : (safeYearMin - YEAR_MIN_BOUND + 1)
  const yearEndHandle = safeYearMax >= YEAR_MAX_UNLIMITED ? YEAR_END_HANDLE_MAX : (safeYearMax - YEAR_MIN_BOUND + 1)

  const kmMinValue = clamp(toNum(value.kmMin, KM_MIN_BOUND), KM_MIN_BOUND, KM_MAX_BOUND)
  const kmMaxValue = value.kmMax == null || value.kmMax === ''
    ? KM_MAX_UNLIMITED
    : clamp(toNum(value.kmMax, KM_MAX_BOUND), KM_MIN_BOUND, KM_MAX_BOUND)
  const safeKmMin = Math.min(kmMinValue, kmMaxValue)
  const safeKmMax = Math.max(kmMinValue, kmMaxValue)

  const priceStartLabel = safePriceMin <= PRICE_MIN_BOUND ? '0만원' : formatPriceManwonLabel(safePriceMin)
  const priceEndLabel = safePriceMax >= PRICE_MAX_UNLIMITED ? '∞만원' : formatPriceManwonLabel(safePriceMax)
  const yearStartLabel = safeYearMin <= 0 ? '0년' : `${safeYearMin.toLocaleString()}년`
  const yearEndLabel = safeYearMax >= YEAR_MAX_UNLIMITED ? '∞년' : `${safeYearMax.toLocaleString()}년`
  const kmStartLabel = safeKmMin <= KM_MIN_BOUND ? '0km' : `${safeKmMin.toLocaleString()}km`
  const kmEndLabel = safeKmMax >= KM_MAX_UNLIMITED ? '∞km' : `${safeKmMax.toLocaleString()}km`

  const priceStartInputValue = safePriceMin <= PRICE_MIN_BOUND ? '' : String(safePriceMin)
  const priceEndInputValue = safePriceMax >= PRICE_MAX_UNLIMITED ? '' : String(Math.min(safePriceMax, PRICE_MAX_BOUND))
  const yearStartInputValue = safeYearMin <= 0 ? '' : String(safeYearMin)
  const yearEndInputValue = safeYearMax >= YEAR_MAX_UNLIMITED ? '' : String(Math.min(safeYearMax, YEAR_MAX_BOUND))
  const kmStartInputValue = safeKmMin <= KM_MIN_BOUND ? '' : String(safeKmMin)
  const kmEndInputValue = safeKmMax >= KM_MAX_UNLIMITED ? '' : String(Math.min(safeKmMax, KM_MAX_BOUND))

  const loadMakers = async () => {
    setMakersLoading(true)
    setMakersError('')
    try {
      const data = await getMakers()
      setMakers(data)
    } catch {
      setMakers([])
      setMakersError('제조사 데이터를 불러오지 못했습니다.')
    } finally {
      setMakersLoading(false)
    }
  }

  useEffect(() => {
    if (!makerCode) { setModelGroups([]); return }
    getModelGroups(makerCode).then(setModelGroups).catch(() => setModelGroups([]))
  }, [makerCode])
  useEffect(() => {
    if (!makerCode) return
    if (makers.length > 0 || makersLoading) return
    loadMakers()
  }, [makerCode, makers.length, makersLoading])
  useEffect(() => {
    if (!makerCode || !modelGroupCode) { setModels([]); return }
    getModels(makerCode, modelGroupCode).then(setModels).catch(() => setModels([]))
  }, [makerCode, modelGroupCode])
  useEffect(() => {
    if (!makerCode || !singleModelGroupCode || !singleModelCode) { setTrims([]); return }
    getTrims(makerCode, singleModelGroupCode, singleModelCode).then(setTrims).catch(() => setTrims([]))
  }, [makerCode, singleModelGroupCode, singleModelCode])

  useEffect(() => {
    if (!makerCode) setSelectedModelGroupByCode({})
  }, [makerCode])

  useEffect(() => {
    setTextQueryDraft(String(value.q ?? ''))
  }, [value.q])

  useEffect(() => {
    const hasQ = String(value.q ?? '').trim().length > 0
    if (hasQ && searchMode !== 'text') {
      setSearchMode('text')
      return
    }
    if (!hasQ && hasStructuredSelection && searchMode !== 'structured') {
      setSearchMode('structured')
    }
  }, [value.q, hasStructuredSelection, searchMode])

  useEffect(() => {
    const hasQ = String(value.q ?? '').trim().length > 0
    if (!hasQ || !hasStructuredSelection) return
    onChange({
      ...value,
      makerCode: undefined,
      modelGroupCode: undefined,
      modelCode: undefined,
      trimCode: undefined,
    })
    setSelectedModelGroupByCode({})
  }, [value, hasStructuredSelection, onChange])

  useEffect(() => {
    if (String(value.q ?? '').trim()) setFiltersCollapsed(true)
  }, [value.q])

  useEffect(() => {
    if (!makerPickerOpen) return
    const prevOverflow = document.body.style.overflow
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setMakerPickerOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = prevOverflow
      window.removeEventListener('keydown', onEsc)
    }
  }, [makerPickerOpen])

  useEffect(() => {
    if (!trimPickerOpen) return
    const prevOverflow = document.body.style.overflow
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setTrimPickerOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = prevOverflow
      window.removeEventListener('keydown', onEsc)
    }
  }, [trimPickerOpen])

  useEffect(() => {
    if (!bodyTypePickerOpen) return
    const prevOverflow = document.body.style.overflow
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setBodyTypePickerOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = prevOverflow
      window.removeEventListener('keydown', onEsc)
    }
  }, [bodyTypePickerOpen])

  useEffect(() => {
    if (!fuelPickerOpen) return
    const prevOverflow = document.body.style.overflow
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setFuelPickerOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = prevOverflow
      window.removeEventListener('keydown', onEsc)
    }
  }, [fuelPickerOpen])

  useEffect(() => {
    if (!modelPickerOpen) return
    const prevOverflow = document.body.style.overflow
    const onEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setModelPickerOpen(false) }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = prevOverflow
      window.removeEventListener('keydown', onEsc)
    }
  }, [modelPickerOpen])

  useEffect(() => {
    if (!modelPickerOpen || !pickerMakerCode) {
      setPickerModelGroups([])
      setPickerModelGroupsLoading(false)
      setPickerModelGroupsError('')
      setPickerModelsByGroup({})
      setPickerModelsLoadingByGroup({})
      setPickerModelsErrorByGroup({})
      setPickerOpenGroupCodes([])
      return
    }
    setPickerModelGroupsLoading(true)
    setPickerModelGroupsError('')
    getModelGroups(pickerMakerCode)
      .then(setPickerModelGroups)
      .catch(() => {
        setPickerModelGroups([])
        setPickerModelGroupsError('모델그룹을 불러오지 못했습니다.')
      })
      .finally(() => setPickerModelGroupsLoading(false))
  }, [modelPickerOpen, pickerMakerCode])

  const ensurePickerGroupModels = (groupCode: string) => {
    if (!pickerMakerCode || (!makerPickerOpen && !modelPickerOpen) || !groupCode) return
    if (pickerModelsByGroup[groupCode] || pickerModelsLoadingByGroup[groupCode]) return

    setPickerModelsLoadingByGroup(prev => ({ ...prev, [groupCode]: true }))
    setPickerModelsErrorByGroup(prev => ({ ...prev, [groupCode]: '' }))
    getModels(pickerMakerCode, groupCode)
      .then(data => {
        setPickerModelsByGroup(prev => ({ ...prev, [groupCode]: data }))
      })
      .catch(() => {
        setPickerModelsByGroup(prev => ({ ...prev, [groupCode]: [] }))
        setPickerModelsErrorByGroup(prev => ({ ...prev, [groupCode]: '모델을 불러오지 못했습니다.' }))
      })
      .finally(() => {
        setPickerModelsLoadingByGroup(prev => ({ ...prev, [groupCode]: false }))
      })
  }

  useEffect(() => {
    if (!modelPickerOpen || !pickerMakerCode) return
    pickerOpenGroupCodes.forEach(code => ensurePickerGroupModels(code))
  }, [
    modelPickerOpen,
    pickerMakerCode,
    pickerOpenGroupCodes,
    pickerModelsByGroup,
    pickerModelsLoadingByGroup,
  ])

  const set = (k: string) => (e: React.ChangeEvent<HTMLSelectElement | HTMLInputElement>) => {
    const v = e.target.value
    const next: Filters = { ...value, [k]: v || undefined }
    if (k === 'makerCode') { next.modelGroupCode = next.modelCode = next.trimCode = next.q = undefined }
    if (k === 'modelGroupCode') { next.modelCode = next.trimCode = next.q = undefined }
    if (k === 'modelCode') { next.trimCode = next.q = undefined }
    if (k === 'makerCode' || k === 'modelGroupCode' || k === 'modelCode') setTextQueryDraft('')
    onChange(next)
  }

  const setBodyTypes = (nextBodyTypes: string[]) => {
    onChange({ ...value, bodyType: nextBodyTypes.length ? nextBodyTypes.join(',') : undefined })
  }

  const setTrim = (nextTrimCode: string | undefined) => {
    onChange({ ...value, trimCode: nextTrimCode || undefined })
  }

  const openBodyTypePicker = () => {
    setBodyTypeDraftCodes(selectedBodyTypes)
    if (bodyTypeItems.length === 0 && !bodyTypeLoading) {
      setBodyTypeLoading(true)
      setBodyTypeError('')
      getBodyTypes()
        .then(setBodyTypeItems)
        .catch(() => {
          setBodyTypeItems([])
          setBodyTypeError('차종 목록을 불러오지 못했습니다.')
        })
        .finally(() => setBodyTypeLoading(false))
    }
    setBodyTypePickerOpen(true)
  }

  const openFuelPicker = () => {
    setFuelDraftCodes(selectedFuels)
    setFuelPickerOpen(true)
  }

  const setPriceStart = (n: number) => {
    const start = clamp(n, PRICE_MIN_BOUND, PRICE_MAX_BOUND)
    const end = Math.max(start, safePriceMax)
    onChange({
      ...value,
      priceMin: start <= PRICE_MIN_BOUND ? undefined : start,
      priceMax: end >= PRICE_MAX_UNLIMITED ? undefined : Math.min(end, PRICE_MAX_BOUND),
    })
  }

  const setPriceEnd = (n: number) => {
    const end = clamp(n, PRICE_MIN_BOUND, PRICE_MAX_UNLIMITED)
    const start = Math.min(safePriceMin, end)
    onChange({
      ...value,
      priceMin: start <= PRICE_MIN_BOUND ? undefined : start,
      priceMax: end >= PRICE_MAX_UNLIMITED ? undefined : Math.min(end, PRICE_MAX_BOUND),
    })
  }

  const setYearStart = (n: number) => {
    const normalized = clamp(n, 0, YEAR_HANDLE_MAX)
    const start = normalized <= 0 ? 0 : (YEAR_MIN_BOUND + normalized - 1)
    const end = Math.max(start, safeYearMax)
    onChange({
      ...value,
      yearMin: start <= 0 ? undefined : start,
      yearMax: end >= YEAR_MAX_UNLIMITED ? undefined : Math.min(Math.max(end, YEAR_MIN_BOUND), YEAR_MAX_BOUND),
    })
  }

  const setYearEnd = (n: number) => {
    const normalized = clamp(n, 1, YEAR_END_HANDLE_MAX)
    const end = normalized >= YEAR_END_HANDLE_MAX ? YEAR_MAX_UNLIMITED : (YEAR_MIN_BOUND + normalized - 1)
    const start = Math.min(safeYearMin, end)
    onChange({
      ...value,
      yearMin: start <= 0 ? undefined : start,
      yearMax: end >= YEAR_MAX_UNLIMITED ? undefined : Math.min(Math.max(end, YEAR_MIN_BOUND), YEAR_MAX_BOUND),
    })
  }

  const setKmStart = (n: number) => {
    const start = clamp(n, KM_MIN_BOUND, KM_MAX_BOUND)
    const end = Math.max(start, safeKmMax)
    onChange({
      ...value,
      kmMin: start <= KM_MIN_BOUND ? undefined : start,
      kmMax: end >= KM_MAX_UNLIMITED ? undefined : Math.min(end, KM_MAX_BOUND),
    })
  }

  const setKmEnd = (n: number) => {
    const end = clamp(n, KM_MIN_BOUND, KM_MAX_UNLIMITED)
    const start = Math.min(safeKmMin, end)
    onChange({
      ...value,
      kmMin: start <= KM_MIN_BOUND ? undefined : start,
      kmMax: end >= KM_MAX_UNLIMITED ? undefined : Math.min(end, KM_MAX_BOUND),
    })
  }

  const setPriceStartInput = (raw: string) => {
    if (raw.trim() === '') { setPriceStart(PRICE_MIN_BOUND); return }
    const n = Number(raw)
    if (!Number.isFinite(n)) return
    setPriceStart(n)
  }

  const setPriceEndInput = (raw: string) => {
    if (raw.trim() === '') { setPriceEnd(PRICE_MAX_UNLIMITED); return }
    const n = Number(raw)
    if (!Number.isFinite(n)) return
    setPriceEnd(n)
  }

  const setYearStartInput = (raw: string) => {
    if (raw.trim() === '') { setYearStart(0); return }
    const n = Number(raw)
    if (!Number.isFinite(n)) return
    setYearStart(n - YEAR_MIN_BOUND + 1)
  }

  const setYearEndInput = (raw: string) => {
    if (raw.trim() === '') { setYearEnd(YEAR_END_HANDLE_MAX); return }
    const n = Number(raw)
    if (!Number.isFinite(n)) return
    setYearEnd(n - YEAR_MIN_BOUND + 1)
  }

  const setKmStartInput = (raw: string) => {
    if (raw.trim() === '') { setKmStart(KM_MIN_BOUND); return }
    const n = Number(raw)
    if (!Number.isFinite(n)) return
    setKmStart(n)
  }

  const setKmEndInput = (raw: string) => {
    if (raw.trim() === '') { setKmEnd(KM_MAX_UNLIMITED); return }
    const n = Number(raw)
    if (!Number.isFinite(n)) return
    setKmEnd(n)
  }

  const resetPriceRange = () => {
    onChange({ ...value, priceMin: undefined, priceMax: undefined })
  }

  const resetYearRange = () => {
    onChange({ ...value, yearMin: undefined, yearMax: undefined })
  }

  const resetKmRange = () => {
    onChange({ ...value, kmMin: undefined, kmMax: undefined })
  }

  const applyMaker = (code?: string) => {
    const next: Filters = {
      ...value,
      makerCode: code || undefined,
      modelGroupCode: undefined,
      modelCode: undefined,
      trimCode: undefined,
      q: undefined,
    }
    setSelectedModelGroupByCode({})
    setSelectedModelNameByCode({})
    setTextQueryDraft('')
    onChange(next)
  }

  const toggleModel = (maker: string, group: string, model: string) => {
    const exists = selectedModelCodes.includes(model)
    const nextCodes = exists
      ? selectedModelCodes.filter(code => code !== model)
      : [...selectedModelCodes, model]
    const nextModelGroupByCode = { ...selectedModelGroupByCode }
    const nextModelNameByCode = { ...selectedModelNameByCode }

    if (exists) {
      delete nextModelGroupByCode[model]
      delete nextModelNameByCode[model]
    } else {
      nextModelGroupByCode[model] = group
      const modelName = pickerModelsByGroup[group]?.find(m => m.code === model)?.name
      if (modelName) nextModelNameByCode[model] = modelName
    }

    const nextModelGroupCode = nextCodes.length === 1
      ? (nextModelGroupByCode[nextCodes[0]] || group)
      : undefined

    onChange({
      ...value,
      makerCode: maker || undefined,
      modelGroupCode: nextModelGroupCode,
      modelCode: nextCodes.length ? nextCodes.join(',') : undefined,
      trimCode: undefined,
      q: undefined,
    })
    setTextQueryDraft('')
    setSelectedModelGroupByCode(nextModelGroupByCode)
    setSelectedModelNameByCode(nextModelNameByCode)
    setPickerOpenGroupCodes(prev => (prev.includes(group) ? prev : [...prev, group]))
    ensurePickerGroupModels(group)
  }

  const selectedPickerGroupCodes = useMemo(() => {
    const setCodes = new Set<string>()
    selectedModelCodes.forEach(code => {
      const group = selectedModelGroupByCode[code]
      if (group) setCodes.add(group)
    })
    if (selectedModelCodes.length === 1 && singleModelGroupCode) setCodes.add(singleModelGroupCode)
    return Array.from(setCodes)
  }, [selectedModelCodes, selectedModelGroupByCode, singleModelGroupCode])

  const selectedPickerGroupCodeSet = useMemo(
    () => new Set(selectedPickerGroupCodes),
    [selectedPickerGroupCodes]
  )

  const togglePickerGroup = (groupCode: string) => {
    const isOpen = pickerOpenGroupCodes.includes(groupCode)
    const isPinned = selectedPickerGroupCodeSet.has(groupCode)

    if (isOpen) {
      if (isPinned) return
      setPickerOpenGroupCodes(prev => prev.filter(code => code !== groupCode))
      return
    }

    setPickerOpenGroupCodes(prev => (prev.includes(groupCode) ? prev : [...prev, groupCode]))
    ensurePickerGroupModels(groupCode)
  }

  const openMakerPicker = () => {
    if (makers.length === 0 && !makersLoading) loadMakers()
    setMakerPickerOpen(true)
    setPickerSearch('')
    setPickerMakerCode(makerCode)
  }

  const openModelPicker = () => {
    if (!makerCode) return
    setPickerMakerCode(makerCode)
    const initialOpenGroups = selectedPickerGroupCodes.length
      ? selectedPickerGroupCodes
      : (modelGroupCode ? [modelGroupCode] : [])
    setPickerOpenGroupCodes(initialOpenGroups)
    setModelPickerOpen(true)
    setPickerSearch('')
  }

  const hasFilters = Object.entries(value).some(([k, v]) =>
    k !== 'page' && k !== 'size' && v !== undefined && v !== ''
  )

  const switchToStructuredMode = () => {
    setSearchMode('structured')
    setTextQueryDraft('')
    if (value.q !== undefined && value.q !== '') {
      onChange({ ...value, q: undefined })
    }
  }

  const switchToTextMode = () => {
    setSearchMode('text')
    setSelectedModelGroupByCode({})
    setSelectedModelNameByCode({})
    const next: Filters = {
      ...value,
      makerCode: undefined,
      modelGroupCode: undefined,
      modelCode: undefined,
      trimCode: undefined,
    }
    if (
      value.makerCode !== undefined
      || value.modelGroupCode !== undefined
      || value.modelCode !== undefined
      || value.trimCode !== undefined
    ) {
      onChange(next)
    }
  }

  const clearStructuredFilters = () => {
    if (!hasStructuredSelection) return
    setSelectedModelGroupByCode({})
    setSelectedModelNameByCode({})
    onChange({
      ...value,
      makerCode: undefined,
      modelGroupCode: undefined,
      modelCode: undefined,
      trimCode: undefined,
    })
  }

  const clearModelFilters = () => {
    if (!modelCode && !trimCode) return
    setSelectedModelGroupByCode({})
    setSelectedModelNameByCode({})
    onChange({
      ...value,
      modelGroupCode: undefined,
      modelCode: undefined,
      trimCode: undefined,
    })
  }

  const resetFilters = () => {
    setTextQueryDraft('')
    setSelectedModelGroupByCode({})
    setSelectedModelNameByCode({})
    onChange({})
  }

  const selectedMaker = makers.find(m => m.code === makerCode)
  const selectedModel = models.find(m => m.code === singleModelCode)
  const selectedTrim = trims.find(t => t.code === trimCode)
  const selectedFuels = parseCsvTokens(value.fuel)
  const selectedModelCodeSet = useMemo(() => new Set(selectedModelCodes), [selectedModelCodes])

  const detailFilterCount = [
    selectedFuels.length ? 'selected' : undefined,
    value.carNo,
    selectedBodyTypes.length ? 'selected' : undefined,
  ].filter(v => v !== undefined && v !== '').length

  const hasPriceRange = value.priceMin != null || value.priceMax != null
  const hasYearRange = value.yearMin != null || value.yearMax != null
  const hasKmRange = value.kmMin != null || value.kmMax != null

  const collapsedSummary = useMemo(() => {
    const chips: string[] = []
    if (String(value.q ?? '').trim()) chips.push(`텍스트 "${String(value.q).trim()}"`)
    if (makerCode) chips.push(selectedMaker ? `제조사 ${selectedMaker.name}` : `제조사 ${makerCode}`)
    if (selectedModelCodes.length > 0) chips.push(`모델 ${selectedModelCodes.length}개`)
    if (trimCode) chips.push('트림 선택')
    if (hasPriceRange) chips.push(`가격 ${priceStartLabel}~${priceEndLabel}`)
    if (hasYearRange) chips.push(`연식 ${yearStartLabel}~${yearEndLabel}`)
    if (hasKmRange) chips.push(`주행거리 ${kmStartLabel}~${kmEndLabel}`)
    if (selectedBodyTypes.length > 0) chips.push(`차종 ${selectedBodyTypes.length}개`)
    if (selectedFuels.length > 0) chips.push(`연료 ${selectedFuels.length}개`)
    if (value.carNo) chips.push(`차량번호 ${String(value.carNo)}`)
    return chips
  }, [
    makerCode,
    selectedMaker,
    selectedModelCodes.length,
    trimCode,
    hasPriceRange,
    priceStartLabel,
    priceEndLabel,
    hasYearRange,
    yearStartLabel,
    yearEndLabel,
    hasKmRange,
    kmStartLabel,
    kmEndLabel,
    selectedBodyTypes.length,
    selectedFuels.length,
    value.carNo,
    value.q,
  ])

  const handleSearch = () => {
    if (searchMode === 'text') {
      const nextQ = textQueryDraft.trim()
      const currentQ = String(value.q ?? '').trim()
      const next: Filters = {
        ...value,
        makerCode: undefined,
        modelGroupCode: undefined,
        modelCode: undefined,
        trimCode: undefined,
        q: nextQ || undefined,
      }
      if (
        nextQ !== currentQ
        || value.makerCode !== undefined
        || value.modelGroupCode !== undefined
        || value.modelCode !== undefined
        || value.trimCode !== undefined
      ) {
        setSelectedModelGroupByCode({})
        onChange(next)
      } else {
        onSearch()
      }
      setFiltersCollapsed(true)
      return
    }

    setTextQueryDraft('')
    if (value.q !== undefined && value.q !== '') onChange({ ...value, q: undefined })
    else onSearch()
    setFiltersCollapsed(true)
  }

  const domesticMakers = useMemo(
    () => makers.filter(isDomesticMaker).slice().sort(sortByCountThenName),
    [makers]
  )

  const importedMakers = useMemo(
    () => makers.filter(m => !isDomesticMaker(m)).slice().sort(sortByCountThenName),
    [makers]
  )

  const keyword = pickerSearch.trim().toLowerCase()
  const matchByKeyword = (item: CodeItem) => !keyword
    || item.name.toLowerCase().includes(keyword)
    || String(item.code).includes(keyword)

  const visibleDomestic = domesticMakers.filter(matchByKeyword)
  const visibleImported = importedMakers.filter(matchByKeyword)

  const activePickerMaker = makers.find(m => m.code === pickerMakerCode)

  const popularGroups = useMemo(
    () => pickerModelGroups.filter(g => countOf(g) > 0).slice().sort(sortByCountThenName).slice(0, 10),
    [pickerModelGroups]
  )

  const nameGroups = useMemo(
    () => pickerModelGroups.slice().sort((a, b) => a.name.localeCompare(b.name, 'ko')),
    [pickerModelGroups]
  )

  const visiblePopularGroups = popularGroups.filter(matchByKeyword)
  const visibleNameGroups = nameGroups.filter(matchByKeyword)
  const visibleModelsByGroup = (groupCode: string) =>
    (pickerModelsByGroup[groupCode] ?? []).filter(matchByKeyword).slice().sort(sortByCountThenName)

  const goBackInPicker = () => {
    setMakerPickerOpen(false)
  }

  const chooseMakerFromPicker = (maker: CodeItem) => {
    if (countOf(maker) <= 0) return
    setPickerMakerCode(maker.code)
    setPickerOpenGroupCodes([])
    setPickerModelsByGroup({})
    setPickerModelsLoadingByGroup({})
    setPickerModelsErrorByGroup({})
    setPickerSearch('')
    applyMaker(maker.code)
    setMakerPickerOpen(false)
  }

  const visibleTrims = trims
    .filter(t => !trimSearch.trim() || t.name.toLowerCase().includes(trimSearch.trim().toLowerCase()))
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name, 'ko'))

  const bodyTypeDraftSet = useMemo(() => new Set(bodyTypeDraftCodes), [bodyTypeDraftCodes])
  const fuelDraftSet = useMemo(() => new Set(fuelDraftCodes), [fuelDraftCodes])
  const bodyTypeCountMap = useMemo(() => {
    const m = new Map<string, number>()
    bodyTypeItems.forEach(it => m.set(it.code, countOf(it)))
    return m
  }, [bodyTypeItems])

  return (
    <>
      {filtersCollapsed ? (
        <div className="card p-3 animate-fade-in">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-semibold text-gray-500">현재 검색 조건</p>
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {(collapsedSummary.length > 0 ? collapsedSummary : ['전체']).map(chip => (
                  <span key={chip} className="badge badge-gray text-[11px]">{chip}</span>
                ))}
              </div>
            </div>
            <button
              className="inline-flex items-center justify-center h-9 w-9 rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 transition shrink-0"
              onClick={() => setFiltersCollapsed(false)}
              aria-label="검색 필터 펼치기"
              title="검색 필터 펼치기"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
          </div>
        </div>
      ) : (
      <div className="card p-4 animate-fade-in">
        <div className="space-y-4">
          <div>
            <div className="rounded-xl border border-gray-200 bg-gray-50 p-1 grid grid-cols-2 gap-1">
              <button
                className={`h-9 rounded-lg text-sm font-semibold transition ${searchMode === 'structured' ? 'bg-white text-brand-700 shadow-sm border border-brand-100' : 'text-gray-500 hover:text-gray-700'}`}
                onClick={switchToStructuredMode}
              >
                제조사/모델/트림
              </button>
              <button
                className={`h-9 rounded-lg text-sm font-semibold transition ${searchMode === 'text' ? 'bg-white text-brand-700 shadow-sm border border-brand-100' : 'text-gray-500 hover:text-gray-700'}`}
                onClick={switchToTextMode}
              >
                텍스트 검색
              </button>
            </div>
          </div>

          {searchMode === 'structured' ? (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="text-xs font-semibold text-gray-500 block mb-1">제조사</label>
                <div className="flex items-center gap-1.5">
                  <button
                    className="flex-1 min-w-0 text-left px-3.5 py-2.5 rounded-xl border border-gray-200 bg-white hover:border-gray-300 transition text-sm flex items-center justify-between"
                    onClick={openMakerPicker}
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      {selectedMaker ? (
                        <>
                          <MakerLogo makerCode={selectedMaker.code} makerName={selectedMaker.name} className="w-6 h-6 rounded-md object-contain bg-white border border-gray-200 p-0.5 shrink-0" />
                          <span className="font-semibold text-gray-800 truncate">{selectedMaker.name}</span>
                        </>
                      ) : (
                        <span className="text-gray-500">선택</span>
                      )}
                    </span>
                    <span className="text-xs text-gray-400">▼</span>
                  </button>
                  {makerCode && (
                    <ClearIconButton
                      onClick={e => {
                        e.stopPropagation()
                        clearStructuredFilters()
                      }}
                      label="제조사 조건 해제"
                    />
                  )}
                </div>
              </div>

              <div>
                <label className="text-xs font-semibold text-gray-500 block mb-1">모델</label>
                <div className="flex items-center gap-1.5">
                  <button
                    disabled={!makerCode}
                    className={`flex-1 min-w-0 text-left px-3.5 py-2.5 rounded-xl border text-sm flex items-center justify-between transition ${makerCode ? 'border-gray-200 bg-white hover:border-gray-300' : 'border-gray-100 bg-gray-50 text-gray-400 cursor-not-allowed'}`}
                    onClick={openModelPicker}
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      {selectedModelCodes.length === 1 ? (
                        <>
                          <ModelLogo modelCode={singleModelCode} modelName={selectedModelNameByCode[singleModelCode] ?? selectedModel?.name ?? ''} className="w-6 h-6 rounded-md object-contain bg-white border border-gray-200 p-0.5 shrink-0" />
                          <span className="font-semibold text-gray-800 truncate">
                            {selectedModelNameByCode[singleModelCode] ?? selectedModel?.name ?? singleModelCode}
                          </span>
                        </>
                      ) : selectedModelCodes.length > 1 ? (
                        <span className="font-semibold text-gray-800 truncate">{selectedModelCodes.length}개 선택</span>
                      ) : (
                        <span>{makerCode ? '선택' : '제조사 먼저 선택'}</span>
                      )}
                    </span>
                    <span className="text-xs text-gray-400">▼</span>
                  </button>
                  {modelCode && (
                    <ClearIconButton
                      onClick={e => {
                        e.stopPropagation()
                        clearModelFilters()
                      }}
                      label="모델 조건 해제"
                    />
                  )}
                </div>
                {selectedModelCodes.length > 1 && (
                  <p className="mt-1 text-[11px] text-gray-400 leading-tight truncate">
                    {selectedModelCodes.map(code => selectedModelNameByCode[code] ?? code).join(' · ')}
                  </p>
                )}
              </div>

              <div>
                <label className="text-xs font-semibold text-gray-500 block mb-1">트림</label>
                <div className="flex items-center gap-1.5">
                  <button
                    disabled={selectedModelCodes.length !== 1 || !singleModelGroupCode}
                    className={`flex-1 min-w-0 text-left px-3.5 py-2.5 rounded-xl border text-sm flex items-center justify-between transition ${selectedModelCodes.length === 1 && !!singleModelGroupCode ? 'border-gray-200 bg-white hover:border-gray-300' : 'border-gray-100 bg-gray-50 text-gray-400 cursor-not-allowed'}`}
                    onClick={() => setTrimPickerOpen(true)}
                  >
                    <span className="truncate">{selectedTrim?.name ?? (selectedModelCodes.length === 1 && !!singleModelGroupCode ? '선택' : '모델 1개 선택 후 가능')}</span>
                    <span className="text-xs text-gray-400">▼</span>
                  </button>
                  {trimCode && (
                    <ClearIconButton
                      onClick={e => {
                        e.stopPropagation()
                        setTrim(undefined)
                      }}
                      label="트림 조건 해제"
                    />
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div>
              <label className="text-xs font-semibold text-gray-500 block mb-1">텍스트 검색</label>
              <input
                className="input text-sm"
                placeholder="예) 기아, BMW, SUV"
                value={textQueryDraft}
                onChange={e => setTextQueryDraft(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    handleSearch()
                  }
                }}
              />
              {String(value.q ?? '').trim() && (
                <p className="mt-1 text-[11px] text-gray-500">
                  현재 텍스트 검색 결과: <span className="font-semibold text-gray-700">"{String(value.q).trim()}"</span>
                </p>
              )}
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
            <RangeBlock
              label="가격"
              trackMin={PRICE_MIN_BOUND}
              trackMax={PRICE_MAX_UNLIMITED}
              startMin={PRICE_MIN_BOUND}
              startMax={PRICE_MAX_BOUND}
              endMin={PRICE_MIN_BOUND}
              endMax={PRICE_MAX_UNLIMITED}
              step={100}
              start={safePriceMin}
              end={safePriceMax}
              startLabel={priceStartLabel}
              endLabel={priceEndLabel}
              unit="만원"
              startInputValue={priceStartInputValue}
              endInputValue={priceEndInputValue}
              startInputMin={PRICE_MIN_BOUND}
              startInputMax={PRICE_MAX_BOUND}
              endInputMin={PRICE_MIN_BOUND}
              endInputMax={PRICE_MAX_BOUND}
              onStart={setPriceStart}
              onEnd={setPriceEnd}
              onStartInput={setPriceStartInput}
              onEndInput={setPriceEndInput}
              onReset={resetPriceRange}
              canReset={hasPriceRange}
              clearLabel="가격 조건 해제"
            />
            <RangeBlock
              label="연식"
              trackMin={0}
              trackMax={YEAR_END_HANDLE_MAX}
              startMin={0}
              startMax={YEAR_HANDLE_MAX}
              endMin={1}
              endMax={YEAR_END_HANDLE_MAX}
              step={1}
              start={yearStartHandle}
              end={yearEndHandle}
              startLabel={yearStartLabel}
              endLabel={yearEndLabel}
              unit="년"
              startInputValue={yearStartInputValue}
              endInputValue={yearEndInputValue}
              startInputMin={YEAR_MIN_BOUND}
              startInputMax={YEAR_MAX_BOUND}
              endInputMin={YEAR_MIN_BOUND}
              endInputMax={YEAR_MAX_BOUND}
              onStart={setYearStart}
              onEnd={setYearEnd}
              onStartInput={setYearStartInput}
              onEndInput={setYearEndInput}
              onReset={resetYearRange}
              canReset={hasYearRange}
              clearLabel="연식 조건 해제"
            />
            <RangeBlock
              label="주행거리"
              trackMin={KM_MIN_BOUND}
              trackMax={KM_MAX_UNLIMITED}
              startMin={KM_MIN_BOUND}
              startMax={KM_MAX_BOUND}
              endMin={KM_MIN_BOUND}
              endMax={KM_MAX_UNLIMITED}
              step={1000}
              start={safeKmMin}
              end={safeKmMax}
              startLabel={kmStartLabel}
              endLabel={kmEndLabel}
              unit="km"
              startInputValue={kmStartInputValue}
              endInputValue={kmEndInputValue}
              startInputMin={KM_MIN_BOUND}
              startInputMax={KM_MAX_BOUND}
              endInputMin={KM_MIN_BOUND}
              endInputMax={KM_MAX_BOUND}
              onStart={setKmStart}
              onEnd={setKmEnd}
              onStartInput={setKmStartInput}
              onEndInput={setKmEndInput}
              onReset={resetKmRange}
              canReset={hasKmRange}
              clearLabel="주행거리 조건 해제"
            />
          </div>

          <div className="flex items-center justify-end gap-2">
            <button
              className={`btn-ghost h-[38px] text-sm ${detailOpen ? 'text-brand-600' : 'text-gray-500'}`}
              onClick={() => setDetailOpen(v => !v)}
            >
              상세 필터
              {detailFilterCount > 0 && (
                <span className="ml-1 w-5 h-5 rounded-full bg-brand-600 text-white text-xs flex items-center justify-center">
                  {detailFilterCount}
                </span>
              )}
            </button>

            <button className="btn-primary h-[38px] px-5" onClick={handleSearch}>
              검색
            </button>

            <button
              className="inline-flex items-center justify-center h-[38px] w-[38px] rounded-lg border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 transition"
              onClick={() => setFiltersCollapsed(true)}
              aria-label="검색 필터 접기"
              title="검색 필터 접기"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
              </svg>
            </button>

            {hasFilters && (
              <button className="btn-ghost h-[38px] text-xs text-red-500 hover:text-red-600 hover:bg-red-50" onClick={resetFilters}>
                초기화
              </button>
            )}
          </div>
        </div>

        {detailOpen && (
          <div className="mt-4 pt-4 border-t border-gray-100 grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-3 gap-3 animate-slide-up">
            <div>
              <label className="text-xs font-semibold text-gray-500 block mb-1">차종</label>
              <button
                className="w-full text-left px-3.5 py-2.5 rounded-xl border border-gray-200 bg-white hover:border-gray-300 transition text-sm flex items-center justify-between"
                onClick={openBodyTypePicker}
              >
                <span className="truncate text-gray-800">
                  {selectedBodyTypes.length > 0 ? `${selectedBodyTypes.length}개 선택` : '전체'}
                </span>
                <span className="text-xs text-gray-400">▼</span>
              </button>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-500 block mb-1">연료</label>
              <button
                className="w-full text-left px-3.5 py-2.5 rounded-xl border border-gray-200 bg-white hover:border-gray-300 transition text-sm flex items-center justify-between"
                onClick={openFuelPicker}
              >
                <span className="truncate text-gray-800">
                  {selectedFuels.length > 0 ? `${selectedFuels.length}개 선택` : '전체'}
                </span>
                <span className="text-xs text-gray-400">▼</span>
              </button>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-500 block mb-1">차량번호</label>
              <input className="input text-sm" placeholder="예) 12가3456" value={String(value.carNo ?? '')} onChange={set('carNo')} />
            </div>
          </div>
        )}
      </div>
      )}

      {makerPickerOpen && (
        <ModalPortal>
        <div className="fixed inset-0 z-[220]">
          <button className="absolute inset-0 bg-black/45" onClick={() => setMakerPickerOpen(false)} />
          <div className="absolute inset-0 md:p-6 md:flex md:items-center md:justify-center">
            <div
              className="h-[100dvh] md:h-auto md:max-h-[88vh] w-full md:max-w-4xl bg-white md:rounded-2xl shadow-2xl overflow-hidden flex flex-col"
              style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
            >
              <ModalHeader
                title="제조사 선택"
                onBack={goBackInPicker}
                onClose={() => setMakerPickerOpen(false)}
              />

              <div className="px-4 sm:px-5 py-3 border-b border-gray-100 bg-white">
                <div className="relative">
                  <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                  <input
                    className="input text-sm pl-9"
                    placeholder="제조사 검색"
                    value={pickerSearch}
                    onChange={e => setPickerSearch(e.target.value)}
                  />
                </div>
              </div>

              <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-5 bg-gray-50">
                {makersLoading && (
                  <div className="text-center text-sm text-gray-500 py-10">제조사 목록 불러오는 중...</div>
                )}

                {!makersLoading && makersError && (
                  <div className="text-center py-10">
                    <p className="text-sm text-gray-500 mb-3">{makersError}</p>
                    <button className="btn-ghost text-xs" onClick={loadMakers}>다시 시도</button>
                  </div>
                )}

                {!makersLoading && !makersError && (
                  <>
                    <div>
                      <p className="text-xs font-semibold text-gray-600 mb-2">국산</p>
                      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2">
                        {visibleDomestic.map(m => (
                          <MakerCard key={`domestic-${m.code}`} maker={m} selected={pickerMakerCode === m.code} onSelect={() => chooseMakerFromPicker(m)} />
                        ))}
                      </div>
                    </div>

                    <div>
                      <p className="text-xs font-semibold text-gray-600 mb-2">외산</p>
                      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2">
                        {visibleImported.map(m => (
                          <MakerCard key={`imported-${m.code}`} maker={m} selected={pickerMakerCode === m.code} onSelect={() => chooseMakerFromPicker(m)} />
                        ))}
                      </div>
                    </div>

                    {visibleDomestic.length === 0 && visibleImported.length === 0 && (
                      <div className="text-center text-sm text-gray-500 py-10">검색 결과가 없습니다.</div>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
        </ModalPortal>
      )}

      {modelPickerOpen && (
        <ModalPortal>
        <div className="fixed inset-0 z-[220]">
          <button className="absolute inset-0 bg-black/45" onClick={() => setModelPickerOpen(false)} />
          <div className="absolute inset-0 md:p-6 md:flex md:items-center md:justify-center">
            <div
              className="h-[100dvh] md:h-auto md:max-h-[88vh] w-full md:max-w-6xl bg-white md:rounded-2xl shadow-2xl overflow-hidden flex flex-col"
              style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
            >
              <ModalHeader
                title="모델 선택"
                onBack={() => setModelPickerOpen(false)}
                onClose={() => setModelPickerOpen(false)}
              />

              <div className="px-4 sm:px-5 py-3 border-b border-gray-100 bg-white">
                <div className="relative">
                  <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                  <input
                    className="input text-sm pl-9"
                    placeholder="모델그룹/모델 검색"
                    value={pickerSearch}
                    onChange={e => setPickerSearch(e.target.value)}
                  />
                </div>
              </div>

              <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-5 bg-gray-50">
                {pickerModelGroupsLoading && (
                  <div className="text-center text-sm text-gray-500 py-10">모델그룹 불러오는 중...</div>
                )}

                {!pickerModelGroupsLoading && pickerModelGroupsError && (
                  <div className="text-center text-sm text-gray-500 py-10">{pickerModelGroupsError}</div>
                )}

                {activePickerMaker && (
                  <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
                    <MakerLogo makerCode={activePickerMaker.code} makerName={activePickerMaker.name} />
                    <span>{activePickerMaker.name}</span>
                  </div>
                )}

                {!pickerModelGroupsLoading && !pickerModelGroupsError && (
                <>
                <div>
                  <p className="text-xs font-semibold text-gray-500 mb-2">인기모델</p>
                  <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
                    {visiblePopularGroups.map(g => {
                      const groupDisabled = countOf(g) <= 0
                      const expanded = pickerOpenGroupCodes.includes(g.code)
                      const pinned = selectedPickerGroupCodeSet.has(g.code)
                      const visibleModels = visibleModelsByGroup(g.code)
                      const modelsLoading = !!pickerModelsLoadingByGroup[g.code]
                      const modelsError = pickerModelsErrorByGroup[g.code] ?? ''
                      return (
                        <div key={`popular-${g.code}`} className="border-b border-gray-100 last:border-b-0">
                          <button
                            disabled={groupDisabled}
                            className={`w-full px-3.5 py-3 text-left flex items-center justify-between ${expanded ? 'bg-brand-50' : ''} ${groupDisabled ? 'opacity-40 cursor-not-allowed' : 'hover:bg-gray-50'}`}
                            onClick={() => togglePickerGroup(g.code)}
                          >
                            <span className={`font-semibold text-sm ${expanded ? 'text-brand-700' : 'text-gray-800'}`}>{g.name}</span>
                            <span className="text-xs text-gray-400">{countOf(g).toLocaleString()}</span>
                          </button>

                          {expanded && (
                            <div className="border-t border-gray-100 bg-white">
                              {modelsLoading && (
                                <div className="px-4 py-6 text-center text-xs text-gray-400">모델 목록 불러오는 중...</div>
                              )}
                              {!modelsLoading && modelsError && (
                                <div className="px-4 py-6 text-center text-xs text-gray-400">{modelsError}</div>
                              )}
                              {!modelsLoading && !modelsError && (
                              <>
                              {visibleModels.map(m => {
                                const modelDisabled = countOf(m) <= 0
                                const modelSelected = selectedModelCodeSet.has(m.code)
                                return (
                                  <button
                                    key={`model-popular-${m.code}`}
                                    disabled={modelDisabled}
                                    className={`w-full px-4 py-2.5 border-b border-gray-100 last:border-b-0 flex items-center gap-2 bg-white ${modelDisabled ? 'opacity-40 cursor-not-allowed' : 'hover:bg-gray-50'}`}
                                    onClick={() => toggleModel(pickerMakerCode, g.code, m.code)}
                                  >
                                    <span className={`w-4 h-4 rounded border flex items-center justify-center text-[11px] font-bold ${modelSelected ? 'bg-brand-600 border-brand-600 text-white' : 'bg-white border-gray-300 text-transparent'}`}>✓</span>
                                    <ModelLogo modelCode={m.code} modelName={m.name} />
                                    <span className="text-sm font-medium text-gray-800 text-left flex-1">{m.name}</span>
                                    <span className="text-xs text-gray-400">{countOf(m).toLocaleString()}</span>
                                  </button>
                                )
                              })}
                              {visibleModels.length === 0 && (
                                <div className="px-4 py-6 text-center text-xs text-gray-400">
                                  {pinned ? '선택된 모델이 있는 그룹입니다.' : '모델이 없습니다.'}
                                </div>
                              )}
                              </>
                              )}
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>

                <div>
                  <p className="text-xs font-semibold text-gray-500 mb-2">이름순</p>
                  <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
                    {visibleNameGroups.map(g => {
                      const groupDisabled = countOf(g) <= 0
                      const expanded = pickerOpenGroupCodes.includes(g.code)
                      const pinned = selectedPickerGroupCodeSet.has(g.code)
                      const visibleModels = visibleModelsByGroup(g.code)
                      const modelsLoading = !!pickerModelsLoadingByGroup[g.code]
                      const modelsError = pickerModelsErrorByGroup[g.code] ?? ''
                      return (
                        <div key={`name-${g.code}`} className="border-b border-gray-100 last:border-b-0">
                          <button
                            disabled={groupDisabled}
                            className={`w-full px-3.5 py-3 text-left flex items-center justify-between ${expanded ? 'bg-brand-50' : ''} ${groupDisabled ? 'opacity-40 cursor-not-allowed' : 'hover:bg-gray-50'}`}
                            onClick={() => togglePickerGroup(g.code)}
                          >
                            <span className={`font-semibold text-sm ${expanded ? 'text-brand-700' : 'text-gray-800'}`}>{g.name}</span>
                            <span className="text-xs text-gray-400">{countOf(g).toLocaleString()}</span>
                          </button>

                          {expanded && (
                            <div className="border-t border-gray-100 bg-white">
                              {modelsLoading && (
                                <div className="px-4 py-6 text-center text-xs text-gray-400">모델 목록 불러오는 중...</div>
                              )}
                              {!modelsLoading && modelsError && (
                                <div className="px-4 py-6 text-center text-xs text-gray-400">{modelsError}</div>
                              )}
                              {!modelsLoading && !modelsError && (
                              <>
                              {visibleModels.map(m => {
                                const modelDisabled = countOf(m) <= 0
                                const modelSelected = selectedModelCodeSet.has(m.code)
                                return (
                                  <button
                                    key={`model-name-${m.code}`}
                                    disabled={modelDisabled}
                                    className={`w-full px-4 py-2.5 border-b border-gray-100 last:border-b-0 flex items-center gap-2 bg-white ${modelDisabled ? 'opacity-40 cursor-not-allowed' : 'hover:bg-gray-50'}`}
                                    onClick={() => toggleModel(pickerMakerCode, g.code, m.code)}
                                  >
                                    <span className={`w-4 h-4 rounded border flex items-center justify-center text-[11px] font-bold ${modelSelected ? 'bg-brand-600 border-brand-600 text-white' : 'bg-white border-gray-300 text-transparent'}`}>✓</span>
                                    <ModelLogo modelCode={m.code} modelName={m.name} />
                                    <span className="text-sm font-medium text-gray-800 text-left flex-1">{m.name}</span>
                                    <span className="text-xs text-gray-400">{countOf(m).toLocaleString()}</span>
                                  </button>
                                )
                              })}
                              {visibleModels.length === 0 && (
                                <div className="px-4 py-6 text-center text-xs text-gray-400">
                                  {pinned ? '선택된 모델이 있는 그룹입니다.' : '모델이 없습니다.'}
                                </div>
                              )}
                              </>
                              )}
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
                </>
                )}
              </div>

              <div className="px-4 sm:px-5 py-3 border-t border-gray-100 bg-white flex items-center justify-between gap-3">
                <p className="text-xs text-gray-500">선택 {selectedModelCodes.length}개</p>
                <div className="flex items-center gap-2">
                  <button
                    className="btn-ghost text-xs text-red-500 hover:text-red-600 hover:bg-red-50"
                    onClick={() => {
                      setSelectedModelGroupByCode({})
                      setSelectedModelNameByCode({})
                      onChange({
                        ...value,
                        modelGroupCode: undefined,
                        modelCode: undefined,
                        trimCode: undefined,
                      })
                    }}
                  >
                    선택 해제
                  </button>
                  <button className="btn-primary h-[36px] px-4 text-sm" onClick={() => setModelPickerOpen(false)}>
                    선택완료
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        </ModalPortal>
      )}

      {trimPickerOpen && (
        <ModalPortal>
        <div className="fixed inset-0 z-[220]">
          <button className="absolute inset-0 bg-black/45" onClick={() => setTrimPickerOpen(false)} />
          <div className="absolute inset-0 md:p-6 md:flex md:items-center md:justify-center">
            <div
              className="h-[100dvh] md:h-auto md:max-h-[70vh] w-full md:max-w-2xl bg-white md:rounded-2xl shadow-2xl overflow-hidden flex flex-col"
              style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
            >
              <ModalHeader
                title="트림 선택"
                onBack={() => setTrimPickerOpen(false)}
                onClose={() => setTrimPickerOpen(false)}
              />

              <div className="px-4 sm:px-5 py-3 border-b border-gray-100 bg-white">
                <div className="relative">
                  <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                  <input className="input text-sm pl-9" placeholder="트림 검색" value={trimSearch} onChange={e => setTrimSearch(e.target.value)} />
                </div>
              </div>

              <div className="flex-1 overflow-y-auto bg-gray-50">
                {visibleTrims.map(t => {
                  const selected = trimCode === t.code
                  return (
                    <button
                      key={t.code}
                      className={`w-full px-4 py-3 border-b border-gray-100 text-left ${selected ? 'bg-brand-50 text-brand-700' : 'bg-white text-gray-800 hover:bg-gray-50'}`}
                      onClick={() => {
                        setTrim(t.code)
                        setTrimPickerOpen(false)
                      }}
                    >
                      <span className="font-medium text-sm">{t.name}</span>
                    </button>
                  )
                })}
                {visibleTrims.length === 0 && <div className="px-4 py-10 text-center text-sm text-gray-500">검색 결과가 없습니다.</div>}
              </div>

              <div className="px-4 sm:px-5 py-3 border-t border-gray-100 bg-white flex justify-end">
                <button
                  className="btn-ghost text-xs text-red-500 hover:text-red-600 hover:bg-red-50"
                  onClick={() => {
                    setTrim(undefined)
                    setTrimPickerOpen(false)
                  }}
                >
                  트림 해제
                </button>
              </div>
            </div>
          </div>
        </div>
        </ModalPortal>
      )}

      {bodyTypePickerOpen && (
        <ModalPortal>
        <div className="fixed inset-0 z-[220]">
          <button className="absolute inset-0 bg-black/45" onClick={() => setBodyTypePickerOpen(false)} />
          <div className="absolute inset-0 md:p-6 md:flex md:items-center md:justify-center">
            <div
              className="h-[100dvh] md:h-auto md:max-h-[70vh] w-full md:max-w-2xl bg-white md:rounded-2xl shadow-2xl overflow-hidden flex flex-col"
              style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
            >
              <ModalHeader
                title="차종 선택"
                onBack={() => setBodyTypePickerOpen(false)}
                onClose={() => setBodyTypePickerOpen(false)}
              />

              <div className="flex-1 overflow-y-auto p-4 sm:p-5 bg-gray-50">
                {bodyTypeLoading && (
                  <div className="text-center text-sm text-gray-500 py-10">차종 목록 불러오는 중...</div>
                )}
                {!bodyTypeLoading && bodyTypeError && (
                  <div className="text-center text-sm text-gray-500 py-10">{bodyTypeError}</div>
                )}
                {!bodyTypeLoading && !bodyTypeError && (
                <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
                  {BODY_TYPE_OPTIONS.filter(Boolean).map(bt => {
                    const selected = bodyTypeDraftSet.has(bt)
                    const count = bodyTypeCountMap.get(bt) ?? 0
                    const disabled = !selected && count <= 0
                    return (
                      <button
                        key={bt}
                        disabled={disabled}
                        className={`w-full px-4 py-3 text-left border-b border-gray-100 last:border-b-0 flex items-center gap-3 transition ${selected ? 'bg-brand-50 text-brand-700' : 'bg-white text-gray-800'} ${disabled ? 'opacity-35 cursor-not-allowed' : 'hover:bg-gray-50'}`}
                        onClick={() => {
                          setBodyTypeDraftCodes(prev =>
                            prev.includes(bt) ? prev.filter(v => v !== bt) : [...prev, bt]
                          )
                        }}
                      >
                        <span className={`w-4 h-4 rounded border flex items-center justify-center text-[11px] font-bold ${selected ? 'bg-brand-600 border-brand-600 text-white' : 'bg-white border-gray-300 text-transparent'}`}>✓</span>
                        <p className="text-sm font-semibold flex-1">{bt}</p>
                        <span className="text-xs text-gray-400">{count.toLocaleString()}대</span>
                      </button>
                    )
                  })}
                </div>
                )}
              </div>

              <div className="px-4 sm:px-5 py-3 border-t border-gray-100 bg-white flex items-center justify-between gap-3">
                <p className="text-xs text-gray-500">선택 {bodyTypeDraftCodes.length}개</p>
                <div className="flex items-center gap-2">
                  <button
                    className="btn-ghost text-xs text-red-500 hover:text-red-600 hover:bg-red-50"
                    onClick={() => setBodyTypeDraftCodes([])}
                  >
                    선택 해제
                  </button>
                  <button
                    className="btn-primary h-[36px] px-4 text-sm"
                    onClick={() => {
                      setBodyTypes(bodyTypeDraftCodes)
                      setBodyTypePickerOpen(false)
                    }}
                  >
                    선택완료
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        </ModalPortal>
      )}

      {fuelPickerOpen && (
        <ModalPortal>
        <div className="fixed inset-0 z-[220]">
          <button className="absolute inset-0 bg-black/45" onClick={() => setFuelPickerOpen(false)} />
          <div className="absolute inset-0 md:p-6 md:flex md:items-center md:justify-center">
            <div
              className="h-[100dvh] md:h-auto md:max-h-[70vh] w-full md:max-w-2xl bg-white md:rounded-2xl shadow-2xl overflow-hidden flex flex-col"
              style={{ paddingTop: 'env(safe-area-inset-top)', paddingBottom: 'env(safe-area-inset-bottom)' }}
            >
              <ModalHeader
                title="연료 선택"
                onBack={() => setFuelPickerOpen(false)}
                onClose={() => setFuelPickerOpen(false)}
              />

              <div className="flex-1 overflow-y-auto p-4 sm:p-5 bg-gray-50">
                <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
                  {FUEL_OPTIONS.map(f => {
                    const selected = fuelDraftSet.has(f)
                    return (
                      <button
                        key={`fuel-${f}`}
                        className="w-full px-4 py-3 text-left border-b border-gray-100 last:border-b-0 flex items-center gap-3 transition hover:bg-gray-50"
                        onClick={() => {
                          setFuelDraftCodes(prev =>
                            prev.includes(f) ? prev.filter(v => v !== f) : [...prev, f]
                          )
                        }}
                      >
                        <span className={`w-4 h-4 rounded border flex items-center justify-center text-[11px] font-bold ${selected ? 'bg-brand-600 border-brand-600 text-white' : 'bg-white border-gray-300 text-transparent'}`}>✓</span>
                        <p className={`text-sm font-semibold flex-1 ${selected ? 'text-brand-700' : 'text-gray-800'}`}>{f}</p>
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className="px-4 sm:px-5 py-3 border-t border-gray-100 bg-white flex items-center justify-between gap-3">
                <p className="text-xs text-gray-500">선택 {fuelDraftCodes.length}개</p>
                <div className="flex items-center gap-2">
                  <button
                    className="btn-ghost text-xs text-red-500 hover:text-red-600 hover:bg-red-50"
                    onClick={() => setFuelDraftCodes([])}
                  >
                    선택 해제
                  </button>
                  <button
                    className="btn-primary h-[36px] px-4 text-sm"
                    onClick={() => {
                      onChange({ ...value, fuel: fuelDraftCodes.length ? fuelDraftCodes.join(',') : undefined })
                      setFuelPickerOpen(false)
                    }}
                  >
                    선택완료
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        </ModalPortal>
      )}
    </>
  )
}
