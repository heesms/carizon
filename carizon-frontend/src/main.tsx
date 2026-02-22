import { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, useLocation, useNavigate, type Location } from 'react-router-dom'
import './main.css'

import Layout from './components/Layout'
import Home from './pages/Home'
import Search from './pages/Search'
import CarDetail from './pages/CarDetail'
import Recommendation from './pages/Recommendation'
import MyLikes from './pages/MyLikes'
import AiRankingBest from './pages/AiRankingBest'
import Info from './pages/Info'
import { applyRouteSeo } from './utils/seo'
import { initGoogleAnalytics, trackPageView } from './utils/analytics'

type AppLocationState = { backgroundLocation?: Location }

function AppRoutes() {
  const location = useLocation()
  const state = (location.state ?? {}) as AppLocationState
  const backgroundLocation = state.backgroundLocation

  useEffect(() => {
    initGoogleAnalytics()
  }, [])

  useEffect(() => {
    const fullPath = `${location.pathname}${location.search}`
    const seoMeta = applyRouteSeo(location.pathname, location.search)
    trackPageView(fullPath, seoMeta.title)
  }, [location.pathname, location.search])

  return (
    <>
      <Routes location={backgroundLocation || location}>
        <Route path="/" element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="search" element={<Search />} />
          <Route path="likes" element={<MyLikes />} />
          <Route path="cars/:id" element={<CarDetail />} />
          <Route path="recommendation" element={<Recommendation />} />
          <Route path="ai-ranking" element={<AiRankingBest />} />
          <Route path="info" element={<Info />} />
        </Route>
      </Routes>

      {backgroundLocation ? <DetailModal /> : null}
    </>
  )
}

function DetailModal() {
  const navigate = useNavigate()

  return (
    <Routes>
      <Route
        path="/cars/:id"
        element={
          <div className="fixed inset-0 z-50">
            <div className="absolute inset-0 bg-black/45 animate-fade-in" onClick={() => navigate(-1)} />
            <div className="absolute inset-0 overflow-y-auto sm:px-4 sm:py-3">
              <div
                className="relative mx-auto w-full h-full sm:max-w-6xl sm:rounded-b-3xl sm:shadow-2xl bg-white animate-slide-down"
                onClick={(e) => e.stopPropagation()}
              >
                <CarDetail onClose={() => navigate(-1)} />
              </div>
            </div>
          </div>
        }
      />
    </Routes>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  </React.StrictMode>
)
