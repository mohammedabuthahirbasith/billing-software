import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import { formatCurrency, formatDateTime } from '../lib/format'
import Card from '../components/Card'
import Button from '../components/Button'
import Badge from '../components/Badge'

export default function InvoiceList() {
  const [invoices, setInvoices] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    apiFetch('/api/invoices')
      .then(setInvoices)
      .catch(() => navigate('/login'))
  }, [navigate])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Invoices</h1>
        <Link to="/invoices/new"><Button>New Invoice</Button></Link>
      </div>

      {invoices ? (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="px-4 py-3">Invoice #</th>
                <th className="px-4 py-3">Customer</th>
                <th className="px-4 py-3 text-right">Total</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Created</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-900">{inv.invoiceNumber}</td>
                  <td className="px-4 py-3 text-slate-500">{inv.customerName || '—'}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(inv.totalAmount)}</td>
                  <td className="px-4 py-3">
                    <Badge tone={inv.status === 'VOID' ? 'danger' : 'success'}>{inv.status}</Badge>
                  </td>
                  <td className="px-4 py-3 text-slate-500">{formatDateTime(inv.createdAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/invoices/${inv.id}`} className="font-medium text-brand-600 hover:text-brand-700">
                      View
                    </Link>
                  </td>
                </tr>
              ))}
              {invoices.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500">No invoices yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </Card>
      ) : <p className="text-slate-500">Loading…</p>}
    </div>
  )
}
