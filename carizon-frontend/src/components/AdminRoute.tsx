import { Navigate } from 'react-router-dom'

export default function AdminRoute({ children }: { children: React.ReactNode }) {
  const isAuth = sessionStorage.getItem('adminAuth') === '1'
  if (!isAuth) {
    return <Navigate to="/admin/login" replace />
  }
  return <>{children}</>
}
