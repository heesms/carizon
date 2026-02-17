import React from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './main.css'
import App from '@/routes/App'
import Home from '@/routes/Home'
import Search from '@/routes/Search'
import CarDetail from '@/routes/CarDetail'
import Recommendation from '@/routes/Recommendation'

const router = createBrowserRouter([
  { path: '/', element: <App />,
    children: [
      { index: true, element: <Home /> },             // 메인 = 간단 AI 추천 + 검색/광고
      { path: '/search', element: <Search /> },
      { path: '/recommendation', element: <Recommendation /> }, // 전체 조건 AI 추천
      { path: '/cars/:id', element: <CarDetail /> },
    ]},
])

createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
