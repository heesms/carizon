import React, { useMemo, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'

const SOURCES = ['ENC','KBCAR','KCAR']
const COLORS  = ['BLACK','WHITE','GRAY','SILVER','BLUE','RED']

export default function Search(){
  const nav = useNavigate()
  const { search } = useLocation()
  const params = useMemo(()=>Object.fromEntries(new URLSearchParams(search)), [search])

  const [model, setModel] = useState(params.model || '')
  const [yearFrom, setYearFrom] = useState(params.yearFrom || '')
  const [yearTo, setYearTo]     = useState(params.yearTo || '')
  const [priceMin, setPriceMin] = useState(params.priceMin || '')
  const [priceMax, setPriceMax] = useState(params.priceMax || '')
  const [mileageMax, setMileageMax] = useState(params.mileageMax || '')
  const [color, setColor]       = useState(params.color || '')
  const [sources, setSources]   = useState((params.sources?.split(',')||[]).filter(Boolean))
  const [plate, setPlate]       = useState(params.plate || '')
  const [sort, setSort]         = useState(params.sort || 'price_asc')

  const toggleSource = (s) => {
    setSources(prev => prev.includes(s) ? prev.filter(x=>x!==s) : [...prev, s])
  }

  const submit = (e) => {
    e?.preventDefault?.()
    const q = new URLSearchParams()
    if (model.trim()) q.set('model', model.trim())
    if (plate.trim()) q.set('plate', plate.trim())
    if (yearFrom) q.set('yearFrom', yearFrom)
    if (yearTo) q.set('yearTo', yearTo)
    if (priceMin) q.set('priceMin', priceMin)
    if (priceMax) q.set('priceMax', priceMax)
    if (mileageMax) q.set('mileageMax', mileageMax)
    if (color) q.set('color', color)
    if (sources.length) q.set('sources', sources.join(','))
    if (sort) q.set('sort', sort)
    nav(`/list?${q.toString()}`)
  }

  const saveAlert = async () => {
    const payload = { model, plate, yearFrom, yearTo, priceMin, priceMax, mileageMax, color, sources, sort, channel:'email', frequency:'DAILY' }
    console.log('Save alert (demo):', payload)
    alert('데모: 알림 조건이 저장되었다고 가정합니다.')
  }

  return (
    <div className="wrap">
      <h2 style={{margin:'0 0 12px'}}>검색</h2>
      <form className="filters" onSubmit={submit}>
        <div className="field">
          <label>모델/트림</label>
          <input value={model} onChange={e=>setModel(e.target.value)} placeholder="예) 그랜저 IG 2.5" />
        </div>
        <div className="field">
          <label>차량번호</label>
          <input value={plate} onChange={e=>setPlate(e.target.value)} placeholder="예) 12가3456" />
        </div>
        <div className="two">
          <div className="field">
            <label>연식 From</label>
            <input type="number" value={yearFrom} onChange={e=>setYearFrom(e.target.value)} placeholder="2019" />
          </div>
          <div className="field">
            <label>연식 To</label>
            <input type="number" value={yearTo} onChange={e=>setYearTo(e.target.value)} placeholder="2024" />
          </div>
        </div>
        <div className="two">
          <div className="field">
            <label>최소가(원)</label>
            <input type="number" value={priceMin} onChange={e=>setPriceMin(e.target.value)} placeholder="15000000" />
          </div>
          <div className="field">
            <label>최대가(원)</label>
            <input type="number" value={priceMax} onChange={e=>setPriceMax(e.target.value)} placeholder="40000000" />
          </div>
        </div>
        <div className="field">
          <label>최대 주행거리(km)</label>
          <input type="number" value={mileageMax} onChange={e=>setMileageMax(e.target.value)} placeholder="60000" />
        </div>
        <div className="field">
          <label>색상</label>
          <select value={color} onChange={e=>setColor(e.target.value)}>
            <option value="">전체</option>
            {['BLACK','WHITE','GRAY','SILVER','BLUE','RED'].map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
        <div className="field">
          <label>출처</label>
          <div style={{display:'flex',gap:12,flexWrap:'wrap'}}>
            {['ENC','KBCAR','KCAR'].map(s => (
              <label key={s}><input type="checkbox" checked={sources.includes(s)} onChange={()=>toggleSource(s)} /> {s}</label>
            ))}
          </div>
        </div>
        <div className="field">
          <label>정렬</label>
          <select value={sort} onChange={e=>setSort(e.target.value)}>
            <option value="price_asc">가격 오름차순</option>
            <option value="price_desc">가격 내림차순</option>
            <option value="mileage_asc">주행 오름차순</option>
            <option value="year_desc">연식 최신순</option>
          </select>
        </div>
        <div className="actions">
          <button type="submit" className="btn btn-primary">검색</button>
          <button type="button" className="btn" onClick={saveAlert}>검색 저장(알림)</button>
        </div>
      </form>
    </div>
  )
}