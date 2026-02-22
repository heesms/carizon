import React, { useEffect, useRef } from 'react'

export default function AdSlot({ id, style }){
  const ref = useRef(null)
  useEffect(()=>{
    // 실제 광고 스크립트 삽입 위치
  },[])
  return (
    <div ref={ref} className="ad" style={style}>
      AD SLOT: {id}
    </div>
  )
}