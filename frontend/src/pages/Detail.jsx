import React, { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fetchDemoDetail } from '../services/demo'
import AdSlot from '../shared/AdSlot'

export default function Detail(){
  const { carUid } = useParams()
  const [car, setCar] = useState(null)

  useEffect(()=>{ fetchDemoDetail(carUid).then(setCar) }, [carUid])

  const priceMap = useMemo(()=> {
    if(!car?.prices) return {}
    return Object.fromEntries(car.prices.map(p=>[p.source, p.price]))
  }, [car])

  if(!car) return <div className="wrap">로딩중…</div>

  const min = Math.min(...car.prices.map(p=>p.price))
  const max = Math.max(...car.prices.map(p=>p.price))

  return (
    <div className="wrap" style={{display:'grid',gap:16}}>
      <AdSlot id="DETAIL_TOP" />
      <div style={{display:'flex',alignItems:'center',gap:16}}>
        <div className="thumb" style={{width:220,height:140}} />
        <div>
          <h2 style={{margin:'0 0 6px'}}>{car.brand} {car.model} {car.trim}</h2>
          <div className="sub">{car.year} · {car.mileage_km.toLocaleString()}km · {car.color}</div>
          <div className="price" style={{fontSize:20}}>{car.price.toLocaleString()}원</div>
          <div className="chips">
            <span className="badge">대표가(중앙값 가정)</span>
            <span className="badge badge--range">최저 {min.toLocaleString()} ~ 최고 {max.toLocaleString()}</span>
          </div>
        </div>
      </div>

      <div className="table">
        <table className="table">
          <thead>
            <tr>
              <th>출처</th>
              <th>가격</th>
              <th>주행거리</th>
              <th>연식</th>
              <th>링크</th>
            </tr>
          </thead>
          <tbody>
            {car.sources.map((s, idx) => {
              const v = priceMap[s.source]
              const cls =
                v === undefined ? '' :
                v === car.price ? 'chip chip--good' :
                (v > car.price ? 'chip chip--bad' : 'chip chip--warn')
              return (
                <tr key={s.source} className={idx%2 ? 'row-alt' : ''}>
                  <td>{s.source}</td>
                  <td>{v ? <span className={cls}>{v.toLocaleString()}원</span> : '-'}</td>
                  <td>{s.mileage_km?.toLocaleString() ?? '-'}</td>
                  <td>{s.year ?? '-'}</td>
                  <td>{s.url ? <a href={s.url} target="_blank" rel="noreferrer">원문 보기</a> : '-'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <AdSlot id="DETAIL_MID" />
      <p className="sub" style={{fontSize:12}}>
        * 가격 칩 색상: 대표가 동일(녹색) / 대표가보다 저렴(노랑) / 더 비쌈(빨강)
      </p>
      <AdSlot id="DETAIL_BOTTOM" />
    </div>
  )
}