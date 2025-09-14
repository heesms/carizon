import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export default function SearchBar({ placeholder='모델명, 차량번호, 연식...' }) {
  const [q, setQ] = useState('')
  const navigate = useNavigate()

  const submit = (e) => {
    e.preventDefault()
    const p = new URLSearchParams()
    if (q.trim()) p.set('q', q.trim())
    navigate(`/list?${p.toString()}`)
  }

  return (
    <form onSubmit={submit} style={{display:'flex',gap:8}}>
      <input
        value={q}
        onChange={e=>setQ(e.target.value)}
        placeholder={placeholder}
        style={{flex:1,height:44,padding:'0 14px',border:'1px solid #cbd5e1',borderRadius:12}}
      />
      <button type="submit" style={{
        height:44,padding:'0 16px',borderRadius:12,fontWeight:800,
        color:'#fff',background:'#0ea5e9',border:'1px solid #0ea5e9',cursor:'pointer'
      }}>검색</button>
    </form>
  )
}