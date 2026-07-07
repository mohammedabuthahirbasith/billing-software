import { Navigate } from 'react-router-dom'
import { getToken } from './api'

export default function ProtectedRoute({ children }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />   // no token → bounce to login
  }
  return children
}