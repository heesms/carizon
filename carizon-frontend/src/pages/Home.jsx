import React from 'react'
import SearchBar from '../components/SearchBar'
import AdSlot from '../shared/AdSlot'

export default function Home(){
  return (
    <div className="wrap">
      <div className="home-grid">
        <section className="section">
          <h2 style={{margin:0,fontSize:26,fontWeight:900}}>여러 플랫폼의 차를 한눈에 — Carizon</h2>
          <p className="sub" style={{margin:'8px 0 16px'}}>
            다양한 중고차 플랫폼의 차량정보를 모아 가격/주행거리/연식을 비교합니다.
          </p>
          <SearchBar />
        </section>
        <div style={{display:'grid',gap:12}}>
          <AdSlot id="HOME_TOP" />
          <AdSlot id="HOME_MID" />
          <AdSlot id="HOME_BOTTOM" />
        </div>
      </div>
    </div>
  )
}