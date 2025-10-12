import React, { useEffect, useMemo, useState } from 'react'
import { getMakers, getModelGroups, getModels, getTrims, getGrades, CodeItem } from '@/services/api'

type Props = {
  value: Record<string, string | number | undefined>
  onChange: (p: Record<string, string | number | undefined>) => void
}

export default function FiltersPanel({ value, onChange }: Props){
  const [makers, setMakers] = useState<CodeItem[]>([])
  const [modelGroups, setModelGroups] = useState<CodeItem[]>([])
  const [models, setModels] = useState<CodeItem[]>([])
  const [trims, setTrims] = useState<CodeItem[]>([])
  const [grades, setGrades] = useState<CodeItem[]>([])

  const makerCode = String(value.makerCode||'')
  const modelGroupCode = String(value.modelGroupCode||'')
  const modelCode = String(value.modelCode||'')
  const trimCode = String(value.trimCode||'')

  useEffect(()=>{ getMakers().then(setMakers) },[])
  useEffect(()=>{
    if (!makerCode){ setModelGroups([]); return }
    getModelGroups(makerCode).then(setModelGroups)
  },[makerCode])
  useEffect(()=>{
    if (!makerCode || !modelGroupCode){ setModels([]); return }
    getModels(makerCode, modelGroupCode).then(setModels)
  },[makerCode, modelGroupCode])
  useEffect(()=>{
    if (!makerCode || !modelGroupCode || !modelCode){ setTrims([]); return }
    getTrims(makerCode, modelGroupCode, modelCode).then(setTrims)
  },[makerCode, modelGroupCode, modelCode])
  useEffect(()=>{
    if (!makerCode || !modelGroupCode || !modelCode || !trimCode){ setGrades([]); return }
    getGrades(makerCode, modelGroupCode, modelCode, trimCode).then(setGrades)
  },[makerCode, modelGroupCode, modelCode, trimCode])

  const sorts = useMemo(()=>[
    {code:'RECENT', name:'최신순'},
    {code:'LOW_PRICE', name:'낮은 가격순'},
    {code:'LOW_KM', name:'적은 주행순'},
    {code:'NEW_YEAR', name:'신형순'},
  ], [])

  const onField = (k: string) => (e: React.ChangeEvent<HTMLSelectElement|HTMLInputElement>) => {
    const v = e.target.value
    const next = { ...value, [k]: v || undefined }
    // 계층 선택 해제 시 하위값 초기화
    if (k === 'makerCode'){ next.modelGroupCode = undefined; next.modelCode = undefined; next.trimCode = undefined; next.gradeCode = undefined }
    if (k === 'modelGroupCode'){ next.modelCode = undefined; next.trimCode = undefined; next.gradeCode = undefined }
    if (k === 'modelCode'){ next.trimCode = undefined; next.gradeCode = undefined }
    if (k === 'trimCode'){ next.gradeCode = undefined }
    onChange(next)
  }

  return (
    <aside className="bg-white border rounded-xl p-4 space-y-3">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">제조사</span>
          <select value={makerCode} onChange={onField('makerCode')} className="border rounded px-2 py-2">
            <option value="">전체</option>
            {makers.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">모델그룹</span>
          <select value={modelGroupCode} onChange={onField('modelGroupCode')} className="border rounded px-2 py-2" disabled={!makerCode}>
            <option value="">전체</option>
            {modelGroups.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">모델</span>
          <select value={modelCode} onChange={onField('modelCode')} className="border rounded px-2 py-2" disabled={!modelGroupCode}>
            <option value="">전체</option>
            {models.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">트림</span>
          <select value={trimCode} onChange={onField('trimCode')} className="border rounded px-2 py-2" disabled={!modelCode}>
            <option value="">전체</option>
            {trims.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">등급</span>
          <select value={String(value.gradeCode||'')} onChange={onField('gradeCode')} className="border rounded px-2 py-2" disabled={!trimCode}>
            <option value="">전체</option>
            {grades.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">정렬</span>
          <select value={String(value.sort||'RECENT')} onChange={onField('sort')} className="border rounded px-2 py-2">
            {sorts.map(s=><option key={s.code} value={s.code}>{s.name}</option>)}
          </select>
        </label>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">최소가격</span>
          <input type="number" placeholder="만원 단위" value={value.priceMin as any || ''} onChange={onField('priceMin')} className="border rounded px-2 py-2"/>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">최대가격</span>
          <input type="number" value={value.priceMax as any || ''} onChange={onField('priceMax')} className="border rounded px-2 py-2"/>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">최소연식</span>
          <input type="number" value={value.yearMin as any || ''} onChange={onField('yearMin')} className="border rounded px-2 py-2"/>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">최대연식</span>
          <input type="number" value={value.yearMax as any || ''} onChange={onField('yearMax')} className="border rounded px-2 py-2"/>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-gray-600">주행거리 ≤ (km)</span>
          <input type="number" value={value.kmMax as any || ''} onChange={onField('kmMax')} className="border rounded px-2 py-2"/>
        </label>
        <div className="flex items-end gap-2">
          <button className="px-3 py-2 rounded bg-gray-100 border" onClick={()=>onChange({})}>초기화</button>
        </div>
      </div>
    </aside>
  )
}
