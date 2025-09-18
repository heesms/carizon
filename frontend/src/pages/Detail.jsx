import React, { useEffect, useState, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { faviconUrl, getDomain, trimTitle } from '../shared/linkUtils'
import AdSlot from '../shared/AdSlot'
import { fetchDemoDetail } from '../services/demo' // ⬅️ 데모 폴백용

export default function Detail(){
  const { carUid } = useParams()
  const [car, setCar] = useState(null)
  const [previews, setPreviews] = useState({})

  // 데모 → 새 포맷으로 변환
  const normalizeFromDemo = (d) => {
    // demo 구조: { price, prices:[{source,price}], sources:[{source,url,...}] }
    const priceList = (d?.prices || []).slice().sort((a,b)=>a.price-b.price)
    const min = priceList.length ? priceList[0].price : d?.price ?? 0
    const max = priceList.length ? priceList[priceList.length-1].price : d?.price ?? 0

    // url 매핑: source명 -> url
    const urlMap = Object.fromEntries((d?.sources||[]).map(s=>[s.source, s.url || '']))

    // 새 포맷의 sources: [{price,url,domain}]
    const sources = priceList.map(p => ({
      price: p.price,
      url: urlMap[p.source] || '',
      domain: getDomain(urlMap[p.source] || '')
    })).filter(s => s.url) // url 없는건 제외

    return {
      car_uid: d.car_uid,
      brand: d.brand,
      model: d.model,
      trim: d.trim,
      year: d.year,
      mileage_km: d.mileage_km,
      color: d.color,
      plate: d.plate,           // 데모엔 없으면 undefined
      dealer_phone: d.dealer_phone, // 데모엔 없으면 undefined
      price_range: { min, max },
      sources
    }
  }

  useEffect(()=>{
    (async()=>{
      try {
        // 1) 실제 API 시도
        const res = await fetch(`/api/v1/cars/${carUid}`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.json()
        // 가격 오름차순 정렬 보장
        data.sources?.sort?.((a,b)=>a.price-b.price)
        setCar(data)
        // eslint-disable-next-line no-console
        console.log('[Detail] API data', data)
      } catch (e) {
        // 2) 실패 시 데모로 폴백
        const demo = await fetchDemoDetail(carUid)
        const normalized = normalizeFromDemo(demo)
        setCar(normalized)
        // eslint-disable-next-line no-console
        console.warn('[Detail] API 실패 → demo로 폴백', e)
      }
    })()
  }, [carUid])

  const rangeText = useMemo(()=>{
    if(!car?.price_range) return ''
    const {min, max} = car.price_range
    return `${min.toLocaleString()}원 ~ ${max.toLocaleString()}원`
  }, [car])

  const youtubeUrl = useMemo(()=>{
    return car?.plate ? `https://www.youtube.com/results?search_query=${encodeURIComponent(car.plate)}` : null
  }, [car])

  // (선택) 링크 프리뷰 API가 있을 때만 사용 (없어도 무시됨)
  useEffect(()=>{
    const fetchPreview = async (url)=>{
      try{
        const r = await fetch(`/api/v1/link-preview?url=${encodeURIComponent(url)}`)
        if(r.ok){
          const j = await r.json()
          setPreviews(prev=>({...prev, [url]: j}))
        }
      }catch(_){ /* no-op */ }
    }
    car?.sources?.forEach(s=> s.url && fetchPreview(s.url))
  }, [car])

  if(!car) return <div className="wrap">로딩중…</div>

  return (
      <div className="wrap" style={{display:'grid',gap:16}}>
        <AdSlot id="DETAIL_TOP" />

        {/* 기본 정보 */}
        <div style={{display:'flex',alignItems:'center',gap:16,flexWrap:'wrap'}}>
          <div className="thumb" style={{width:220,height:140,overflow:'hidden'}}>
            <img
                src={`/car-images/grandeur.png`}
                alt={`${car.brand} ${car.model}`}
                style={{width:'100%',height:'100%',objectFit:'cover'}}
            />
          </div>
          <div>
            <h2 style={{margin:'0 0 6px'}}>{car.brand} {car.model} {car.trim}</h2>
            <div className="sub">{car.year} · {car.mileage_km?.toLocaleString()}km · {car.color}</div>
            {car.price_range && (
                <div className="price" style={{fontSize:20}}>
                  이 차의 판매 금액은 {rangeText} 입니다
                </div>
            )}
            <div className="chips" style={{marginTop:8}}>
              {car.dealer_phone && <>
                <a className="btn" href={`tel:${car.dealer_phone}`}>딜러에게 전화</a>
                <a className="btn" href={`sms:${car.dealer_phone}`}>문자 보내기</a>
              </>}
              {youtubeUrl && <a className="btn" target="_blank" rel="noreferrer" href={youtubeUrl}>유튜브 차량번호 검색</a>}
            </div>
          </div>
        </div>

        {/* 플랫폼 프리뷰 카드 (가격 오름차순) */}
        <section className="card">
          <h3 style={{marginTop:0}}>플랫폼으로 가기</h3>
          <div className="grid" style={{gridTemplateColumns:'repeat(auto-fill,minmax(260px,1fr))'}}>
            {car.sources?.map((s, i) => {
              const pv = previews[s.url] || {}
              return (
                  <a key={i} href={s.url} target="_blank" rel="noreferrer" className="card" style={{display:'grid',gap:8}}>
                    <div style={{display:'flex',alignItems:'center',gap:10}}>
                      <img src={faviconUrl(s.url)} alt="" style={{width:16,height:16}} />
                      <div className="title" style={{fontSize:14}}>{s.domain || getDomain(s.url)}</div>
                      <div className="chip" style={{marginLeft:'auto'}}>{s.price.toLocaleString()}원</div>
                    </div>
                    {pv.image && <img src={pv.image} alt="" style={{width:'100%',height:120,objectFit:'cover',borderRadius:8}} />}
                    <div className="sub">{trimTitle(pv.title || '보러가기')}</div>
                  </a>
              )
            })}
            {(!car.sources || car.sources.length===0) && (
                <div className="sub">연결 가능한 플랫폼 링크가 없습니다.</div>
            )}
          </div>
        </section>

        {/* 비교 테이블 (선택) */}
        {car.sources?.length > 0 && (
            <div className="table">
              <table className="table">
                <thead><tr><th>도메인</th><th>가격</th><th>링크</th></tr></thead>
                <tbody>
                {car.sources.map((s, idx) => (
                    <tr key={idx} className={idx%2 ? 'row-alt' : ''}>
                      <td>{s.domain || getDomain(s.url)}</td>
                      <td>{s.price.toLocaleString()}원</td>
                      <td><a href={s.url} target="_blank" rel="noreferrer">바로가기</a></td>
                    </tr>
                ))}
                </tbody>
              </table>
            </div>
        )}

        <AdSlot id="DETAIL_MID" />
        <AdSlot id="DETAIL_BOTTOM" />
      </div>
  )
}
