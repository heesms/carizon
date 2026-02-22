import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './main.css'

import Layout    from './components/Layout'
import Home      from './pages/Home'
import Search    from './pages/Search'
import CarDetail from './pages/CarDetail'
import Recommendation from './pages/Recommendation'
import MyLikes from './pages/MyLikes'
import AiRankingBest from './pages/AiRankingBest'
import Info from './pages/Info'

const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true,         element: <Home /> },
      { path: 'search',      element: <Search /> },
      { path: 'likes',       element: <MyLikes /> },
      { path: 'cars/:id',    element: <CarDetail /> },
      { path: 'recommendation', element: <Recommendation /> },
      { path: 'ai-ranking', element: <AiRankingBest /> },
      { path: 'info',        element: <Info /> },
    ],
  },
])

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
)
