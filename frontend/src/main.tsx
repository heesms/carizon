import React from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './main.css'
import App from './routes/App'
import Search from './routes/Search'
import CarDetail from './routes/CarDetail'

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { path: '/', element: <Search /> },
      { path: '/search', element: <Search /> },
      { path: '/cars/:id', element: <CarDetail /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(<RouterProvider router={router} />)
