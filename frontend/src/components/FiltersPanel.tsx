import React, { useEffect, useState } from 'react'
import { getMakers,getModelGroups,getModels,getTrims,getGrades, type CodeItem } from '@/services/api'

type Props = {
  value: Record<string, string | number | undefined>
  onChange: (p: Record<string, string | number | undefined>) => void
  onSearch: () => void
}

export default function FiltersPanel({ value, onChange, onSearch }: Props){
  const [makers,setMakers]=useState<CodeItem[]>([])
  const [modelGroups,setModelGroups]=useState<CodeItem[]>([])
  const [models,setModels]=useState<CodeItem[]>([])
  const [trims,setTrims]=useState<CodeItem[]>([])
  const [grades,setGrades]=useState<CodeItem[]>([])

  const makerCode=String(value.makerCode||'')
  const modelGroupCode=String(value.modelGroupCode||'')
  const modelCode=String(value.modelCode||'')
  const trimCode=String(value.trimCode||'')

  useEffect(()=>{ getMakers().then(setMakers).catch(()=>setMakers([])) },[])
  useEffect(()=>{ if(!makerCode){setModelGroups([]);return} getModelGroups(makerCode).then(setModelGroups).catch(()=>setModelGroups([])) },[makerCode])
  useEffect(()=>{ if(!makerCode||!modelGroupCode){setModels([]);return} getModels(makerCode,modelGroupCode).then(setModels).catch(()=>setModels([])) },[makerCode,modelGroupCode])
  useEffect(()=>{ if(!makerCode||!modelGroupCode||!modelCode){setTrims([]);return} getTrims(makerCode,modelGroupCode,modelCode).then(setTrims).catch(()=>setTrims([])) },[makerCode,modelGroupCode,modelCode])
  useEffect(()=>{ if(!makerCode||!modelGroupCode||!modelCode||!trimCode){setGrades([]);return} getGrades(makerCode,modelGroupCode,modelCode,trimCode).then(setGrades).catch(()=>setGrades([])) },[makerCode,modelGroupCode,modelCode,trimCode])

  const onField=(k:string)=>(e:React.ChangeEvent<HTMLSelectElement|HTMLInputElement>)=>{
    const v=e.target.value
    const next={...value,[k]:v||undefined}
    if(k==='makerCode'){next.modelGroupCode=next.modelCode=next.trimCode=next.gradeCode=undefined}
    if(k==='modelGroupCode'){next.modelCode=next.trimCode=next.gradeCode=undefined}
    if(k==='modelCode'){next.trimCode=next.gradeCode=undefined}
    if(k==='trimCode'){next.gradeCode=undefined}
    onChange(next)
  }

  return (
      <aside className="bg-white border rounded-xl p-4 space-y-3 lg:sticky lg:top-4 lg:h-fit">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <Field label="제조사">
            <select value={makerCode} onChange={onField('makerCode')} className="border rounded px-2 py-2">
              <option value="">전체</option>
              {makers.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
            </select>
          </Field>
          <Field label="모델그룹">
            <select value={modelGroupCode} onChange={onField('modelGroupCode')} className="border rounded px-2 py-2" disabled={!makerCode}>
              <option value="">전체</option>
              {modelGroups.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
            </select>
          </Field>
          <Field label="모델">
            <select value={modelCode} onChange={onField('modelCode')} className="border rounded px-2 py-2" disabled={!modelGroupCode}>
              <option value="">전체</option>
              {models.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
            </select>
          </Field>
          <Field label="트림">
            <select value={trimCode} onChange={onField('trimCode')} className="border rounded px-2 py-2" disabled={!modelCode}>
              <option value="">전체</option>
              {trims.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
            </select>
          </Field>
          <Field label="등급">
            <select value={String(value.gradeCode||'')} onChange={onField('gradeCode')} className="border rounded px-2 py-2" disabled={!trimCode}>
              <option value="">전체</option>
              {grades.map(m=><option key={m.code} value={m.code}>{m.name}</option>)}
            </select>
          </Field>
          <Field label="정렬">
            <select value={String(value.sort||'RECENT')} onChange={onField('sort')} className="border rounded px-2 py-2">
              <option value="RECENT">최신순</option>
              <option value="LOW_PRICE">낮은 가격순</option>
              <option value="LOW_KM">적은 주행순</option>
              <option value="NEW_YEAR">신형순</option>
            </select>
          </Field>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Field label="최소가격"><input type="number" placeholder="만원" value={value.priceMin as any || ''} onChange={onField('priceMin')} className="border rounded px-2 py-2"/></Field>
          <Field label="최대가격"><input type="number" value={value.priceMax as any || ''} onChange={onField('priceMax')} className="border rounded px-2 py-2"/></Field>
          <Field label="최소연식"><input type="number" value={value.yearMin as any || ''} onChange={onField('yearMin')} className="border rounded px-2 py-2"/></Field>
          <Field label="최대연식"><input type="number" value={value.yearMax as any || ''} onChange={onField('yearMax')} className="border rounded px-2 py-2"/></Field>
          <Field label="주행 ≤ (km)"><input type="number" value={value.kmMax as any || ''} onChange={onField('kmMax')} className="border rounded px-2 py-2"/></Field>
          <div className="flex items-end gap-2">
            <button className="px-3 py-2 rounded border" onClick={()=>onChange({})}>초기화</button>
            <button className="px-3 py-2 rounded bg-black text-white" onClick={onSearch}>검색</button>
          </div>
        </div>
      </aside>
  )
}
function Field({label,children}:{label:string;children:React.ReactNode}){
  return (<label className="flex flex-col gap-1 text-sm"><span className="text-gray-600">{label}</span>{children}</label>)
}
