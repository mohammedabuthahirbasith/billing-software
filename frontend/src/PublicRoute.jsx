import { Navigate } from 'react-router-dom'
import { getToken } from './api'

export default function PublicRoute({ children }) {
  if (getToken()) {
    return <Navigate to="/" replace />   // already logged in → go to dashboard
  }
  return children
}