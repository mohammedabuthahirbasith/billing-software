import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getRole } from '../api'

// UX guard only — the backend's @PreAuthorize is the real gate. Bounces a CASHIER who typed an
// OWNER-only URL straight back to the dashboard instead of letting them watch the page 403.
export function useRequireOwner() {
  const navigate = useNavigate()
  useEffect(() => {
    if (getRole() !== 'OWNER') navigate('/')
  }, [navigate])
}
