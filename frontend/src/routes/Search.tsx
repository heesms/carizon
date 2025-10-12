import React, { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import FiltersPanel from '@/components/FiltersPanel'
import CarCard from '@/components/CarCard'
import { searchCars, type CarListItem, type CarListResponse } from '@/services/api'

export default function Search(){
  const [sp, setSp] = useSearchParams()
  const [list, setList] = useState<CarListItem[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)

  const params = useMemo(()=> Object.fromEntries(sp.entries()), [sp])

  const doFetch = async()=>{
    setLoading(true)
    const res: CarListResponse = await searchCars({ ...params, page })
    setList(res.content)
    setTotalPages(res.totalPages)
    setLoading(false)
  }

  useEffect(()=>{ setPage(Number(params.page||0)) }, [params.page])
  useEffect(()=>{ doFetch() }, [params, page])

  const setFilters = (v: Record<string, any>)=>{
    const usp = new URLSearchParams()
    Object.entries(v).forEach(([k,val])=> { if (val!==undefined && val!=='') usp.set(k, String(val)) })
    usp.set('page','0') // reset page on filter change
    setSp(usp)
  }

  return (
    <div className="max-w-6xl mx-auto p-4 grid grid-cols-1 md:grid-cols-[320px_1fr] gap-4">
      <FiltersPanel value={params} onChange={setFilters} />
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold">검색 결과</h2>
          {loading && <span className="text-sm text-gray-500">로딩 중…</span>}
        </div>
        <div className="grid gap-3">
          {list.map(it => <CarCard key={it.carId} item={it} />)}
          {!loading && list.length===0 && <div className="text-sm text-gray-500">결과가 없습니다.</div>}
        </div>
        <Pagination page={page} totalPages={totalPages} onChange={(p)=> setSp(prev=>{ prev.set('page', String(p)); return prev })}/>
      </section>
    </div>
  )
}

function Pagination({ page, totalPages, onChange }:{ page:number, totalPages:number, onChange:(p:number)=>void }){
  if (totalPages<=1) return null
  const pages = Array.from({length: totalPages}, (_,i)=>i)
  return (
    <div className="flex gap-1 flex-wrap">
      {pages.map(p =>
        <button key={p} onClick={()=>onChange(p)}
          className={"px-3 py-1 rounded border " + (p===page ? "bg-black text-white" : "bg-white")}>
          {p+1}
        </button>
      )}
    </div>
  )
}
