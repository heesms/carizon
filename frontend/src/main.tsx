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
      { index: true, element: <Home /> },       // 홈 유지
      { path: '/search', element: <Search /> }, // 검색
      { path: '/recommendation', element: <Recommendation /> }, // AI 추천
      { path: '/cars/:id', element: <CarDetail /> }, // 상세
    ]},
])

createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
