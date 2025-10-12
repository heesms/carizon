import React, { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import FiltersPanel from '@/components/FiltersPanel'
import CarCard from '@/components/CarCard'
import { searchCars, type CarListItem, type CarListResponse } from '@/services/api'
import AdSlot from '@/components/AdSlot'

export default function Search(){
  const [sp,setSp]=useSearchParams()
  const [list,setList]=useState<CarListItem[]>([])
  const [page,setPage]=useState(0)
  const [totalPages,setTotalPages]=useState(0)
  const [loading,setLoading]=useState(false)
  const params=useMemo(()=>Object.fromEntries(sp.entries()),[sp])

  const fetchPage=async(p=0)=>{
    setLoading(true)
    const res:CarListResponse = await searchCars({ ...params, page:p })
    setList(res.content); setTotalPages(res.totalPages); setLoading(false)
  }

  useEffect(()=>{ fetchPage(0) },[]) // 첫 진입시 자동 조회
  useEffect(()=>{ setPage(Number(params.page||0)) },[params.page])

  const setFilters=(v:Record<string,any>)=>{
    const usp=new URLSearchParams()
    Object.entries(v).forEach(([k,val])=>{ if(val!==undefined&&val!=='') usp.set(k,String(val)) })
    usp.set('page','0'); setSp(usp)
  }
  const onSearch=()=>fetchPage(0)

  return (
      <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-4">
        <FiltersPanel value={params} onChange={setFilters} onSearch={onSearch}/>
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">검색 결과</h2>
            {loading && <span className="text-sm text-gray-500">로딩 중…</span>}
          </div>
          <AdSlot id="list_top" className="h-16" />
          <div className="grid gap-3">
            {list.map(it => <CarCard key={it.carId} item={it} />)}
            {!loading && list.length===0 && <div className="text-sm text-gray-500">조건에 맞는 결과가 없습니다. 필터를 조정해 보세요.</div>}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={(p)=>{ setSp(prev=>{ prev.set('page', String(p)); return prev }); fetchPage(p) }}/>
          <AdSlot id="list_bottom" className="h-16" />
        </section>
      </div>
  )
}

function Pagination({page,totalPages,onChange}:{page:number;totalPages:number;onChange:(p:number)=>void}){
  if(totalPages<=1) return null
  const pages=Array.from({length:totalPages},(_,i)=>i)
  return (
      <div className="flex gap-1 flex-wrap">
        {pages.map(p=>
            <button key={p} onClick={()=>onChange(p)}
                    className={"px-3 py-1 rounded border "+(p===page?"bg-black text-white":"bg-white")}>
              {p+1}
            </button>)}
      </div>
  )
}
