import React, { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { fetchDemoList } from '../services/demo'
import AdSlot from '../shared/AdSlot'

function priceRange(prices){
  if(!prices?.length) return null
  const vals = prices.map(p=>p.price).sort((a,b)=>a-b)
  const min = vals[0], max = vals[vals.length-1]
  return min===max ? `${min.toLocaleString()}원` : `${min.toLocaleString()} ~ ${max.toLocaleString()}원`
}

export default function List(){
  const { search } = useLocation()
  const qs = useMemo(()=>Object.fromEntries(new URLSearchParams(search)), [search])
  const [items, setItems] = useState([])
  const [page, setPage] = useState(Number(qs.page||1))
  const size = 10

  useEffect(()=>{
    fetchDemoList(qs.model || qs.q || '').then(setItems)
  }, [search])

  const total = items.length
  const pageCount = Math.max(1, Math.ceil(total/size))
  const pageItems = items.slice((page-1)*size, page*size)

  return (
    <div className="wrap">
      <AdSlot id="LIST_TOP" style={{marginBottom:12}} />

      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:8}}>
        <h2 style={{margin:0}}>리스트</h2>
        <div style={{color:'#64748b',fontSize:14}}>정렬: {qs.sort || 'price_asc'} · 결과 {total}건</div>
      </div>

      <div className="grid">
        {pageItems.map((car, idx) => {
          const range = priceRange(car.prices)
          const spread = Math.max(...car.prices.map(p=>p.price)) - Math.min(...car.prices.map(p=>p.price))
          const spreadChip =
            spread < 1000000 ? 'chip chip--good' :
            spread < 3000000 ? 'chip chip--warn' : 'chip chip--bad'
          return (
            <React.Fragment key={car.car_uid}>
              {idx===0 && <AdSlot id="LIST_INLINE_1" />}
              {idx===5 && <AdSlot id="LIST_INLINE_2" />}

              <Link to={`/car/${car.car_uid}`} style={{textDecoration:'none',color:'inherit'}}>
                <div className="card grid grid-2">
                  <div className="thumb" />
                  <div>
                    <div style={{display:'flex',gap:8,alignItems:'baseline',flexWrap:'wrap'}}>
                      <div className="title">{car.brand} {car.model} {car.trim}</div>
                      <div className="sub">{car.year} · {car.mileage_km.toLocaleString()}km · {car.color}</div>
                    </div>
                    <div className="price">{car.price.toLocaleString()}원</div>
                    <div className="chips">
                      <span className="badge badge--range">범위 {range}</span>
                      <span className="chip">출처 {car.prices.length}곳</span>
                      <span className={spreadChip}>가격편차 {spread.toLocaleString()}원</span>
                    </div>
                    <div className="chips">
                      {car.prices.map(p => (
                        <span key={p.source} className="chip">{p.source}: {p.price.toLocaleString()}원</span>
                      ))}
                    </div>
                  </div>
                </div>
              </Link>
            </React.Fragment>
          )
        })}
        {pageItems.length === 0 && <div className="sub">검색 결과가 없습니다.</div>}
      </div>

      <AdSlot id="LIST_BOTTOM" style={{marginTop:12}} />

      <div style={{display:'flex',gap:6,justifyContent:'center',marginTop:12}}>
        {Array.from({length:pageCount}, (_,i)=>i+1).map(p=>(
          <button key={p} onClick={()=>setPage(p)}
            style={{height:36,minWidth:36,padding:'0 10px',borderRadius:10,cursor:'pointer',
              border:'1px solid #cbd5e1', background: p===page ? '#e2e8f0' : '#fff'}}>{p}</button>
        ))}
      </div>
    </div>
  )
}