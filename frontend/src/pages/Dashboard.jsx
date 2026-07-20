import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import Card from '../components/Card'

export default function Dashboard() {
  const [user, setUser] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    apiFetch('/api/me')
      .then(setUser)
      .catch(() => navigate('/login'))   // token invalid/expired
  }, [navigate])

  if (!user) return <p className="text-slate-500">Loading…</p>

  return (
    <div className="space-y-6">
      <Card>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Welcome, {user.email}
        </h1>
        <p className="mt-1 text-sm text-slate-500">Signed in as {user.role}</p>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2">
        <Link to="/products">
          <Card className="h-full transition-shadow hover:shadow-md">
            <h2 className="text-lg font-semibold text-slate-900">Products</h2>
            <p className="mt-1 text-sm text-slate-500">Manage your product catalog and stock.</p>
          </Card>
        </Link>
        <Link to="/invoices">
          <Card className="h-full transition-shadow hover:shadow-md">
            <h2 className="text-lg font-semibold text-slate-900">Invoices</h2>
            <p className="mt-1 text-sm text-slate-500">Create sales, view history, and void invoices.</p>
          </Card>
        </Link>
      </div>
    </div>
  )
}
