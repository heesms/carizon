import React from 'react'
import type { PricePoint } from '@/services/api'
export default function PriceChart({ points,width=600,height=220 }:{points:PricePoint[];width?:number;height?:number}){
  if(!points||points.length===0) return <div className="text-sm text-gray-500">가격 이력이 없습니다.</div>
  const pad={l:40,r:10,t:10,b:20}, xs=points.map(p=>+new Date(p.ts)), ys=points.map(p=>p.price)
  const xmin=Math.min(...xs), xmax=Math.max(...xs), ymin=Math.min(...ys), ymax=Math.max(...ys)
  const X=(t:number)=>pad.l + ((t-xmin)/Math.max(1,xmax-xmin))*(width-pad.l-pad.r)
  const Y=(v:number)=>pad.t + (1-((v-ymin)/Math.max(1,ymax-ymin)))*(height-pad.t-pad.b)
  const d=points.map((p,i)=>`${i?'L':'M'} ${X(+new Date(p.ts)).toFixed(1)} ${Y(p.price).toFixed(1)}`).join(' ')
  const yTicks=[ymin,Math.round((ymin+ymax)/2),ymax], xTicks=[xmin,xmax]
  return (
      <svg width={width} height={height} className="bg-white border rounded">
        <g stroke="#e5e7eb">
          <line x1={pad.l} y1={height-pad.b} x2={width-pad.r} y2={height-pad.b} />
          <line x1={pad.l} y1={pad.t} x2={pad.l} y2={height-pad.b} />
          {yTicks.map((v,i)=><g key={i}><line x1={pad.l} y1={Y(v)} x2={width-pad.r} y2={Y(v)} /><text x={4} y={Y(v)-2} fontSize="10" fill="#6b7280">{v.toLocaleString()}</text></g>)}
          {xTicks.map((t,i)=><text key={i} x={X(t)} y={height-4} fontSize="10" textAnchor="middle" fill="#6b7280">{new Date(t).toLocaleDateString()}</text>)}
        </g>
        <path d={d} fill="none" stroke="#111827" strokeWidth="2"/>
        {points.map((p,i)=><circle key={i} cx={X(+new Date(p.ts))} cy={Y(p.price)} r="3" fill="#111827"/>)}
      </svg>
  )
}
