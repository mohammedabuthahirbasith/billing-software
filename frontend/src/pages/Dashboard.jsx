import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import { formatCurrency, formatDateTime, toISODate } from '../lib/format'
import Card from '../components/Card'
import StatCard from '../components/StatCard'
import Loading from '../components/Loading'
import InvoiceStatusBadge from '../components/InvoiceStatusBadge'
import { Table, TableHead, Th, Td, Tr, EmptyRow } from '../components/Table'

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

  if (!user) return <Loading />

  const isOwner = user.role === 'OWNER'

  return (
    <div className="space-y-6">
      <Card>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Welcome, {user.email}</h1>
        <p className="mt-1 text-sm text-slate-500">{user.storeName} — Signed in as {user.role}</p>
      </Card>

      {isOwner && todayReport && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <StatCard label="Today's Revenue" value={formatCurrency(todayReport.totalRevenue)} />
          <StatCard label="Today's GST" value={formatCurrency(todayReport.totalTax)} />
          <StatCard label="Today's Invoices" value={todayReport.invoiceCount} />
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
        <Table className="mt-3">
          <TableHead>
            <Th>Invoice #</Th>
            <Th>Customer</Th>
            <Th align="right">Total</Th>
            <Th>Status</Th>
            <Th>Created</Th>
          </TableHead>
          <tbody>
            {recentInvoices?.map((inv) => (
              <Tr key={inv.id} onClick={() => navigate(`/invoices/${inv.id}`)}>
                <Td className="font-medium text-slate-900">{inv.invoiceNumber}</Td>
                <Td className="text-slate-500">{inv.customerName || '—'}</Td>
                <Td align="right" className="tabular-nums">{formatCurrency(inv.totalAmount)}</Td>
                <Td><InvoiceStatusBadge status={inv.status} /></Td>
                <Td className="text-slate-500">{formatDateTime(inv.createdAt)}</Td>
              </Tr>
            ))}
            {recentInvoices && recentInvoices.length === 0 && (
              <EmptyRow colSpan={5}>No invoices yet.</EmptyRow>
            )}
            {!recentInvoices && <EmptyRow colSpan={5}>Loading…</EmptyRow>}
          </tbody>
        </Table>
      </Card>
    </div>
  )
}