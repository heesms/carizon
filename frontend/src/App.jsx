import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Header from './components/Header'
import Home from './pages/Home'
import Search from './pages/Search'
import List from './pages/List'
import Detail from './pages/Detail'

export default function App() {
  return (
    <div className="app">
      <Header />
      <main className="wrap">
        <Routes>
          <Route path="/" element={<Home/>} />
          <Route path="/search" element={<Search/>} />
          <Route path="/list" element={<List/>} />
          <Route path="/car/:carUid" element={<Detail/>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}