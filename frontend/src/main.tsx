import React from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom'
import './main.css'
import App from './routes/App'
import Search from './routes/Search'
import CarDetail from './routes/CarDetail'

const router = createBrowserRouter([
  { path: '/', element: <App />,
    children: [
      { index: true, element: <Navigate to="/search" replace /> }, // 홈 진입시 검색으로
      { path: '/search', element: <Search /> },
      { path: '/cars/:id', element: <CarDetail /> },
    ]},
])
createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
