import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import { formatCurrency, formatDateTime } from '../lib/format'
import Card from '../components/Card'
import Badge from '../components/Badge'

function toISODate(date) {
  return date.toISOString().slice(0, 10)
}

export default function Dashboard() {
  const [user, setUser] = useState(null)
  const [todayReport, setTodayReport] = useState(null)
  const [recentInvoices, setRecentInvoices] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    apiFetch('/api/me')
      .then(setUser)
      .catch(() => navigate('/login'))   // token invalid/expired
  }, [navigate])

  // Waits for /api/me to resolve first so a CASHIER's dashboard never fires the OWNER-only
  // reports call at all (avoids a noisy, entirely expected 403 on every load).
  useEffect(() => {
    if (!user) return
    apiFetch('/api/invoices').then((invoices) => setRecentInvoices(invoices.slice(0, 5)))
    if (user.role === 'OWNER') {
      const today = toISODate(new Date())
      apiFetch(`/api/reports/sales?from=${today}&to=${today}&topN=5`).then(setTodayReport)
    }
  }, [user])

  if (!user) return <p className="text-slate-500">Loading…</p>

  const isOwner = user.role === 'OWNER'

  return (
    <div className="space-y-6">
      <Card>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Welcome, {user.email}</h1>
        <p className="mt-1 text-sm text-slate-500">{user.storeName} — Signed in as {user.role}</p>
      </Card>

      {isOwner && todayReport && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Card>
            <p className="text-sm text-slate-500">Today's Revenue</p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">
              {formatCurrency(todayReport.totalRevenue)}
            </p>
          </Card>
          <Card>
            <p className="text-sm text-slate-500">Today's GST</p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">
              {formatCurrency(todayReport.totalTax)}
            </p>
          </Card>
          <Card>
            <p className="text-sm text-slate-500">Today's Invoices</p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{todayReport.invoiceCount}</p>
          </Card>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Link to="/invoices/new">
          <Card className="h-full transition-shadow hover:shadow-md">
            <h2 className="text-lg font-semibold text-slate-900">New Invoice</h2>
            <p className="mt-1 text-sm text-slate-500">Start a new sale.</p>
          </Card>
        </Link>
        {isOwner && (
          <Link to="/products/new">
            <Card className="h-full transition-shadow hover:shadow-md">
              <h2 className="text-lg font-semibold text-slate-900">Add Product</h2>
              <p className="mt-1 text-sm text-slate-500">Add to your catalog.</p>
            </Card>
          </Link>
        )}
        {isOwner && (
          <Link to="/reports">
            <Card className="h-full transition-shadow hover:shadow-md">
              <h2 className="text-lg font-semibold text-slate-900">View Reports</h2>
              <p className="mt-1 text-sm text-slate-500">Sales, GST, top products.</p>
            </Card>
          </Link>
        )}
        {isOwner && (
          <Link to="/staff/new">
            <Card className="h-full transition-shadow hover:shadow-md">
              <h2 className="text-lg font-semibold text-slate-900">Add Staff</h2>
              <p className="mt-1 text-sm text-slate-500">Create a cashier login.</p>
            </Card>
          </Link>
        )}
      </div>

      <Card className="overflow-x-auto p-0">
        <div className="flex items-center justify-between px-4 pt-4">
          <h2 className="text-lg font-semibold text-slate-900">Recent Invoices</h2>
          <Link to="/invoices" className="text-sm font-medium text-brand-600 hover:text-brand-700">
            View all
          </Link>
        </div>
        <table className="mt-3 w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <th className="px-4 py-3">Invoice #</th>
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3 text-right">Total</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Created</th>
            </tr>
          </thead>
          <tbody>
            {recentInvoices?.map((inv) => (
              <tr
                key={inv.id}
                onClick={() => navigate(`/invoices/${inv.id}`)}
                className="cursor-pointer border-b border-slate-100 last:border-0 hover:bg-slate-50"
              >
                <td className="px-4 py-3 font-medium text-slate-900">{inv.invoiceNumber}</td>
                <td className="px-4 py-3 text-slate-500">{inv.customerName || '—'}</td>
                <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(inv.totalAmount)}</td>
                <td className="px-4 py-3">
                  <Badge tone={inv.status === 'VOID' ? 'danger' : 'success'}>{inv.status}</Badge>
                </td>
                <td className="px-4 py-3 text-slate-500">{formatDateTime(inv.createdAt)}</td>
              </tr>
            ))}
            {recentInvoices && recentInvoices.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-slate-500">No invoices yet.</td>
              </tr>
            )}
            {!recentInvoices && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-slate-500">Loading…</td>
              </tr>
            )}
          </tbody>
        </table>
      </Card>
    </div>
  )
}