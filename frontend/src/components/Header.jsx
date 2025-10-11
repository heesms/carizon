import React from 'react'
import { Link, useLocation } from 'react-router-dom'

export default function Header(){
  const { pathname } = useLocation()

  return (
      <header className="header">
        <div className="header__inner">
          <Link to="/" className="header__brand" style={{textDecoration:'none',color:'inherit'}}>
            <span className="text-2xl">🚗</span>
            <span style={{fontWeight:800}}>Carizon</span>
          </Link>

          <nav className="header__nav">
            <Link to="/" className={pathname === '/' ? 'is-active' : ''}>메인</Link>
            <Link to="/search" className={pathname.startsWith('/search') ? 'is-active' : ''}>검색</Link>
            <Link to="/list" className={pathname.startsWith('/list') ? 'is-active' : ''}>리스트</Link>
          </nav>
        </div>
      </header>
  )
}
