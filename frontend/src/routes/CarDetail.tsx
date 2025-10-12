import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getCarDetail, getPriceHistory, type CarDetail as CarDetailType, type PricePoint } from '@/services/api'
import PriceChart from '@/components/PriceChart'
import AdSlot from '@/components/AdSlot'

export default function CarDetail(){
  const { id } = useParams()
  const [detail,setDetail]=useState<CarDetailType|null>(null)
  const [platformCarId,setPlatformCarId]=useState<number|undefined>(undefined)
  const [history,setHistory]=useState<PricePoint[]>([])

  useEffect(()=>{ if(!id) return; getCarDetail(id).then(setDetail).catch(()=>setDetail(null)) },[id])
  useEffect(()=>{ if(!id) return; getPriceHistory(id,platformCarId).then(r=>setHistory(r.points||[])).catch(()=>setHistory([])) },[id,platformCarId])

  const platforms=detail?.platforms||[]

  return (
      <div className="space-y-6">
        <AdSlot id="detail_top" className="h-16" />
        <div>
          <h1 className="text-xl font-bold">차량 상세</h1>
          {detail && <div className="text-sm text-gray-700 mt-1">{detail.specs.maker} {detail.specs.model} {detail.specs.trim} · {detail.specs.year} · {detail.specs.km?.toLocaleString()} km</div>}
        </div>

        <section className="bg-white border rounded-xl p-4">
          <h2 className="font-semibold mb-3">플랫폼별 매물</h2>
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="text-gray-500"><tr><th className="text-left p-2">플랫폼</th><th className="text-left p-2">가격</th><th className="text-left p-2">상태</th><th className="text-left p-2">링크</th><th className="text-left p-2">최근관측</th></tr></thead>
              <tbody>
              {platforms.map(p=>(
                  <tr key={p.platformCarId} className="border-t">
                    <td className="p-2">
                      <label className="inline-flex items-center gap-2 cursor-pointer">
                        <input type="radio" name="pcid" onChange={()=>setPlatformCarId(p.platformCarId)} checked={platformCarId===p.platformCarId}/>
                        <span>{p.platform}</span>
                      </label>
                    </td>
                    <td className="p-2 font-medium">{p.price?.toLocaleString()} 원</td>
                    <td className="p-2">{p.status}</td>
                    <td className="p-2">{p.pcUrl ? <a href={p.pcUrl} target="_blank" className="text-blue-600 underline">원문</a> : '-'}</td>
                    <td className="p-2">{p.lastSeenDate || '-'}</td>
                  </tr>
              ))}
              {platforms.length===0 && <tr><td className="p-2 text-gray-500" colSpan={5}>플랫폼 데이터가 없습니다.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>

        <section className="bg-white border rounded-xl p-4">
          <h2 className="font-semibold mb-3">가격 히스토리</h2>
          <PriceChart points={history}/>
        </section>

        <AdSlot id="detail_bottom" className="h-16" />
      </div>
  )
}
