import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, clearToken } from '../api'

export default function Dashboard() {
  const [user, setUser] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    apiFetch('/api/me')
      .then(setUser)
      .catch(() => navigate('/login'))   // token invalid/expired
  }, [navigate])

  function handleLogout() {
    clearToken()
    navigate('/login')
  }

  return (
    <div style={{ maxWidth: 600, margin: '3rem auto', fontFamily: 'sans-serif' }}>
      <h1>Billing Software</h1>
      {user ? (
        <>
          <p>Welcome, <strong>{user.email}</strong> — role: {user.role}</p>
          <button onClick={handleLogout}>Log out</button>
        </>
      ) : <p>Loading…</p>}
    </div>
  )
}