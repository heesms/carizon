import { Link, useNavigate } from 'react-router-dom'

export default function NotFound() {
  const navigate = useNavigate()

  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center text-center px-4 animate-fade-in">
      <div className="text-7xl sm:text-8xl font-black text-gray-100 select-none mb-2">404</div>
      <div className="text-4xl mb-4">🔍</div>
      <h1 className="text-xl sm:text-2xl font-black text-gray-900 mb-2">페이지를 찾을 수 없어요</h1>
      <p className="text-sm text-gray-500 mb-8 max-w-xs">
        주소가 잘못됐거나 삭제된 페이지입니다.
      </p>
      <div className="flex flex-col sm:flex-row gap-3">
        <Link to="/" className="btn-primary px-6">
          홈으로
        </Link>
        <Link to="/search" className="btn-ghost px-6 border border-gray-200">
          차량 검색
        </Link>
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="btn-ghost px-6 text-gray-500"
        >
          이전 페이지
        </button>
      </div>
    </div>
  )
}
