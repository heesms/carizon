import React, { useEffect, useState } from 'react'
import { getMakers,getModelGroups,getModels,getTrims,getGrades, type CodeItem } from '@/services/api'
import CustomSelect from './CustomSelect'

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

  const hasActiveFilters = Object.keys(value).some(k => value[k] !== undefined && value[k] !== '' && k !== 'page' && k !== 'size')

  return (
      <aside className="modern-card p-6 lg:p-8 space-y-6 lg:sticky lg:top-24 lg:h-fit animate-slide-in">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-black to-gray-700 rounded-xl flex items-center justify-center">
              <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
              </svg>
            </div>
            <h3 className="text-2xl font-bold">필터</h3>
          </div>
          {hasActiveFilters && (
            <button
              onClick={() => onChange({})}
              className="text-sm text-gray-500 hover:text-gray-700 transition font-medium flex items-center gap-1"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
              초기화
            </button>
          )}
        </div>

        <div className="space-y-6">
          {/* 차량 정보 필터 */}
          <div>
            <h4 className="text-sm font-bold text-gray-900 mb-4 flex items-center gap-2">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              차량 정보
            </h4>
            <div className="space-y-4">
              <Field label="제조사" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                </svg>
              }>
                <CustomSelect
                  value={makerCode}
                  onChange={(v) => onField('makerCode')({ target: { value: v } } as any)}
                  options={[
                    { value: '', label: '전체 제조사' },
                    ...makers.map(m => ({ value: m.code, label: m.name }))
                  ]}
                  placeholder="전체 제조사"
                />
              </Field>
              <Field label="모델그룹" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              }>
                <CustomSelect
                  value={modelGroupCode}
                  onChange={(v) => onField('modelGroupCode')({ target: { value: v } } as any)}
                  options={[
                    { value: '', label: '전체 모델그룹' },
                    ...modelGroups.map(m => ({ value: m.code, label: m.name }))
                  ]}
                  placeholder="전체 모델그룹"
                  disabled={!makerCode}
                />
              </Field>
              <Field label="모델" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
              }>
                <CustomSelect
                  value={modelCode}
                  onChange={(v) => onField('modelCode')({ target: { value: v } } as any)}
                  options={[
                    { value: '', label: '전체 모델' },
                    ...models.map(m => ({ value: m.code, label: m.name }))
                  ]}
                  placeholder="전체 모델"
                  disabled={!modelGroupCode}
                />
              </Field>
              <Field label="트림" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                </svg>
              }>
                <CustomSelect
                  value={trimCode}
                  onChange={(v) => onField('trimCode')({ target: { value: v } } as any)}
                  options={[
                    { value: '', label: '전체 트림' },
                    ...trims.map(m => ({ value: m.code, label: m.name }))
                  ]}
                  placeholder="전체 트림"
                  disabled={!modelCode}
                />
              </Field>
              <Field label="등급" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
                </svg>
              }>
                <CustomSelect
                  value={String(value.gradeCode||'')}
                  onChange={(v) => onField('gradeCode')({ target: { value: v } } as any)}
                  options={[
                    { value: '', label: '전체 등급' },
                    ...grades.map(m => ({ value: m.code, label: m.name }))
                  ]}
                  placeholder="전체 등급"
                  disabled={!trimCode}
                />
              </Field>
              <Field label="정렬" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 4h13M3 8h9m-9 4h6m4 0l4-4m0 0l4 4m-4-4v12" />
                </svg>
              }>
                <CustomSelect
                  value={String(value.sort||'RECENT')}
                  onChange={(v) => onField('sort')({ target: { value: v } } as any)}
                  options={[
                    { value: 'RECENT', label: '최신순' },
                    { value: 'LOW_PRICE', label: '낮은 가격순' },
                    { value: 'LOW_KM', label: '적은 주행순' },
                    { value: 'NEW_YEAR', label: '신형순' }
                  ]}
                  placeholder="정렬 선택"
                />
              </Field>
            </div>
          </div>

          {/* 가격 및 연식 필터 */}
          <div className="pt-6 border-t border-gray-200">
            <h4 className="text-sm font-bold text-gray-900 mb-4 flex items-center gap-2">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              가격 및 연식
            </h4>
            <div className="grid grid-cols-2 gap-4">
              <Field label="최소가격" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              }>
                <div className="relative">
                  <input 
                    type="number" 
                    placeholder="0" 
                    value={value.priceMin as any || ''} 
                    onChange={onField('priceMin')} 
                    className="input-modern pr-12"
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-gray-500 font-medium">만원</span>
                </div>
              </Field>
              <Field label="최대가격" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              }>
                <div className="relative">
                  <input 
                    type="number" 
                    placeholder="9999" 
                    value={value.priceMax as any || ''} 
                    onChange={onField('priceMax')} 
                    className="input-modern pr-12"
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-gray-500 font-medium">만원</span>
                </div>
              </Field>
              <Field label="최소연식" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              }>
                <div className="relative">
                  <input 
                    type="number" 
                    placeholder="2000" 
                    value={value.yearMin as any || ''} 
                    onChange={onField('yearMin')} 
                    className="input-modern pr-12"
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-gray-500 font-medium">년</span>
                </div>
              </Field>
              <Field label="최대연식" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              }>
                <div className="relative">
                  <input 
                    type="number" 
                    placeholder="2024" 
                    value={value.yearMax as any || ''} 
                    onChange={onField('yearMax')} 
                    className="input-modern pr-12"
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-gray-500 font-medium">년</span>
                </div>
              </Field>
              <Field label="주행거리" icon={
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              }>
                <div className="relative">
                  <input 
                    type="number" 
                    placeholder="100000" 
                    value={value.kmMax as any || ''} 
                    onChange={onField('kmMax')} 
                    className="input-modern pr-12"
                  />
                  <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-gray-500 font-medium">km</span>
                </div>
              </Field>
            </div>
          </div>

          <button 
            className="btn-primary w-full mt-2 flex items-center justify-center gap-2" 
            onClick={onSearch}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            검색하기
          </button>
        </div>
      </aside>
  )
}

function Field({label, icon, children}:{label:string; icon?: React.ReactNode; children:React.ReactNode}){
  return (
    <label className="flex flex-col gap-2">
      <span className="text-sm font-semibold text-gray-700 flex items-center gap-2">
        {icon}
        {label}
      </span>
      {children}
    </label>
  )
}
