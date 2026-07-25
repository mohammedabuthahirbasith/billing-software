import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { formatCurrency, formatDateTime } from '../lib/format'
import Card from '../components/Card'
import Badge from '../components/Badge'
import Button from '../components/Button'
import { useToast } from '../hooks/useToast'

export default function InvoiceDetail() {
  const { id } = useParams()
  const [invoice, setInvoice] = useState(null)
  const [returns, setReturns] = useState([])
  const [returnQuantities, setReturnQuantities] = useState({})
  const [error, setError] = useState(null)
  const [isVoiding, setIsVoiding] = useState(false)
  const [isReturning, setIsReturning] = useState(false)
  const navigate = useNavigate()
  const showToast = useToast()
  const isOwner = getRole() === 'OWNER'

  useEffect(() => {
    Promise.all([apiFetch(`/api/invoices/${id}`), apiFetch(`/api/invoices/${id}/returns`)])
      .then(([invoiceData, returnsData]) => {
        setInvoice(invoiceData)
        setReturns(returnsData)
      })
      .catch(() => navigate('/invoices'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  async function refresh() {
    const [invoiceData, returnsData] = await Promise.all([
      apiFetch(`/api/invoices/${id}`),
      apiFetch(`/api/invoices/${id}/returns`),
    ])
    setInvoice(invoiceData)
    setReturns(returnsData)
  }

  async function handleVoid() {
    if (!window.confirm('Void this invoice? This cannot be undone.')) return
    setError(null)
    setIsVoiding(true)
    try {
      const updated = await apiFetch(`/api/invoices/${id}/void`, { method: 'POST' })
      setInvoice(updated)
      showToast('Invoice voided')
    } catch (err) {
      setError(err.message)
    } finally {
      setIsVoiding(false)
    }
  }

  async function handleReturn() {
    const items = Object.entries(returnQuantities)
      .filter(([, qty]) => Number(qty) > 0)
      .map(([invoiceItemId, qty]) => ({ invoiceItemId: Number(invoiceItemId), quantity: Number(qty) }))

    if (items.length === 0) return

    setError(null)
    setIsReturning(true)
    try {
      const result = await apiFetch(`/api/invoices/${id}/returns`, { method: 'POST', body: JSON.stringify({ items }) })
      setReturnQuantities({})
      await refresh()
      showToast(`Return processed — refunded ${formatCurrency(result.refundTotal)}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setIsReturning(false)
    }
  }

  if (!invoice) return <p className="text-slate-500">Loading…</p>

  // Client-side display hint only — the backend's own aggregate query is the sole source of truth
  // for accepting/rejecting a return, so this can never diverge into a real correctness bug even if
  // it were ever stale.
  const returnedByItemId = {}
  for (const ret of returns) {
    for (const item of ret.items) {
      returnedByItemId[item.invoiceItemId] = (returnedByItemId[item.invoiceItemId] || 0) + item.quantityReturned
    }
  }

  const canReturn = isOwner && invoice.status === 'COMPLETED'
  const hasReturnInput = Object.values(returnQuantities).some((qty) => Number(qty) > 0)

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
          <p className="mt-1">Payment: {invoice.paymentMethod}</p>
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
                <th className="px-4 py-2 text-right">Returnable</th>
                {canReturn && <th className="px-4 py-2 text-right">Return Qty</th>}
              </tr>
            </thead>
            <tbody>
              {invoice.items.map((item) => {
                const remaining = item.quantity - (returnedByItemId[item.id] || 0)
                return (
                  <tr key={item.id} className="border-b border-slate-100 last:border-0">
                    <td className="px-4 py-2 font-medium text-slate-900">{item.productName}</td>
                    <td className="px-4 py-2 text-slate-500">{item.sku}</td>
                    <td className="px-4 py-2 text-slate-500">{item.hsnCode || '—'}</td>
                    <td className="px-4 py-2 text-right tabular-nums">{formatCurrency(item.unitPrice)}</td>
                    <td className="px-4 py-2 text-right tabular-nums">{item.gstRate}%</td>
                    <td className="px-4 py-2 text-right tabular-nums">{item.quantity}</td>
                    <td className="px-4 py-2 text-right tabular-nums">{formatCurrency(item.lineTotal)}</td>
                    <td className="px-4 py-2 text-right tabular-nums text-slate-500">{remaining}</td>
                    {canReturn && (
                      <td className="px-4 py-2 text-right">
                        {remaining > 0 && (
                          <input
                            type="number"
                            min="0"
                            max={remaining}
                            value={returnQuantities[item.id] ?? ''}
                            onChange={(e) =>
                              setReturnQuantities((prev) => ({ ...prev, [item.id]: e.target.value }))
                            }
                            className="w-16 rounded border border-slate-300 px-2 py-1 text-right tabular-nums focus:border-brand-500 focus:outline-none"
                          />
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
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
          <div className="ml-auto flex items-center gap-3">
            {canReturn && (
              <Button variant="secondary" disabled={!hasReturnInput} loading={isReturning} onClick={handleReturn}>
                Process Return
              </Button>
            )}
            {isOwner && invoice.status === 'COMPLETED' && (
              <Button variant="danger" loading={isVoiding} onClick={handleVoid}>Void invoice</Button>
            )}
          </div>
        </div>
      </Card>

      {returns.length > 0 && (
        <Card className="mt-6">
          <h2 className="text-lg font-semibold text-slate-900">Return History</h2>
          <div className="mt-4 space-y-4">
            {returns.map((ret) => (
              <div key={ret.id} className="rounded-lg border border-slate-200 p-4 text-sm">
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">{formatDateTime(ret.createdAt)}</span>
                  <span className="font-semibold text-slate-900">
                    Refunded {formatCurrency(ret.refundTotal)}
                  </span>
                </div>
                <ul className="mt-2 space-y-1 text-slate-600">
                  {ret.items.map((item) => (
                    <li key={item.invoiceItemId}>
                      {item.quantityReturned} × {item.productName} ({item.sku}) — {formatCurrency(item.lineTotalRefund)}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}