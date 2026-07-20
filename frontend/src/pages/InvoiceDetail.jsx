import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { formatCurrency, formatDateTime } from '../lib/format'
import Card from '../components/Card'
import Badge from '../components/Badge'
import Button from '../components/Button'

export default function InvoiceDetail() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState(null)
  const [error, setError] = useState(null)
  const navigate = useNavigate()
  const isOwner = getRole() === 'OWNER'

  useEffect(() => {
    apiFetch(`/api/invoices/${id}`)
      .then(setInvoice)
      .catch(() => navigate('/invoices'))
  }, [id, navigate])

  async function handleVoid() {
    if (!window.confirm('Void this invoice? This cannot be undone.')) return
    setError(null)
    try {
      const updated = await apiFetch(`/api/invoices/${id}/void`, { method: 'POST' })
      setInvoice(updated)
    } catch (err) {
      setError(err.message)
    }
  }

  if (!invoice) return <p className="text-slate-500">Loading…</p>

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">{invoice.invoiceNumber}</h1>
            <p className="mt-1 text-sm text-slate-500">{formatDateTime(invoice.createdAt)}</p>
          </div>
          <Badge tone={invoice.status === 'VOID' ? 'danger' : 'success'}>{invoice.status}</Badge>
        </div>

        <div className="mt-4 border-t border-slate-200 pt-4 text-sm text-slate-600">
          <p>Customer: {invoice.customerName || '—'} {invoice.customerPhone ? `(${invoice.customerPhone})` : ''}</p>
          {invoice.voidedAt && <p className="mt-1 text-rose-600">Voided: {formatDateTime(invoice.voidedAt)}</p>}
        </div>

        <div className="mt-6 overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="px-4 py-2">Product</th>
                <th className="px-4 py-2">SKU</th>
                <th className="px-4 py-2">HSN</th>
                <th className="px-4 py-2 text-right">Unit Price</th>
                <th className="px-4 py-2 text-right">GST %</th>
                <th className="px-4 py-2 text-right">Qty</th>
                <th className="px-4 py-2 text-right">Line Total</th>
              </tr>
            </thead>
            <tbody>
              {invoice.items.map((item, i) => (
                <tr key={i} className="border-b border-slate-100 last:border-0">
                  <td className="px-4 py-2 font-medium text-slate-900">{item.productName}</td>
                  <td className="px-4 py-2 text-slate-500">{item.sku}</td>
                  <td className="px-4 py-2 text-slate-500">{item.hsnCode || '—'}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatCurrency(item.unitPrice)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{item.gstRate}%</td>
                  <td className="px-4 py-2 text-right tabular-nums">{item.quantity}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatCurrency(item.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex justify-end">
          <div className="w-full max-w-xs space-y-1 text-sm">
            <div className="flex justify-between text-slate-600">
              <span>Subtotal</span>
              <span className="tabular-nums">{formatCurrency(invoice.subtotal)}</span>
            </div>
            <div className="flex justify-between text-slate-600">
              <span>Tax</span>
              <span className="tabular-nums">{formatCurrency(invoice.taxAmount)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-1 text-base font-bold text-slate-900">
              <span>Total</span>
              <span className="tabular-nums">{formatCurrency(invoice.totalAmount)}</span>
            </div>
          </div>
        </div>

        {error && <p className="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

        <div className="mt-6 flex items-center gap-3 border-t border-slate-200 pt-6">
          <Link to="/invoices" className="text-sm font-medium text-slate-600 hover:text-slate-900">← Invoices</Link>
          {isOwner && invoice.status === 'COMPLETED' && (
            <Button variant="danger" className="ml-auto" onClick={handleVoid}>Void invoice</Button>
          )}
        </div>
      </Card>
    </div>
  )
}
