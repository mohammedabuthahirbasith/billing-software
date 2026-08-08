import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { withMinDelay } from '../lib/withMinDelay'
import { formatCurrency, formatDateTime } from '../lib/format'
import Card from '../components/Card'
import Button from '../components/Button'
import ErrorText from '../components/ErrorText'
import Loading from '../components/Loading'
import InvoiceStatusBadge from '../components/InvoiceStatusBadge'
import { Table, TableHead, Th, Td, Tr } from '../components/Table'
import { useToast } from '../hooks/useToast'
import { useConfirm } from '../hooks/useConfirm'
import { useDiscardGuard } from '../hooks/useDiscardGuard'

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
  const confirm = useConfirm()
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
    const ok = await confirm({
      title: 'Void this invoice?',
      message: 'This cannot be undone.',
      confirmLabel: 'Void invoice',
      danger: true,
    })
    if (!ok) return
    setError(null)
    setIsVoiding(true)
    try {
      const updated = await withMinDelay(apiFetch(`/api/invoices/${id}/void`, { method: 'POST' }))
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
      const result = await withMinDelay(apiFetch(`/api/invoices/${id}/returns`, { method: 'POST', body: JSON.stringify({ items }) }))
      setReturnQuantities({})
      await refresh()
      showToast(`Return processed — refunded ${formatCurrency(result.refundTotal)}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setIsReturning(false)
    }
  }

  const hasReturnInput = Object.values(returnQuantities).some((qty) => Number(qty) > 0)
  const handleBackClick = useDiscardGuard({
    isDirty: hasReturnInput,
    to: '/invoices',
    title: 'Discard return quantities?',
    message: "You've entered return quantities that haven't been submitted yet.",
  })

  if (!invoice) return <Loading />

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

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">{invoice.invoiceNumber}</h1>
            <p className="mt-1 text-sm text-slate-500">{formatDateTime(invoice.createdAt)}</p>
          </div>
          <InvoiceStatusBadge status={invoice.status} />
        </div>

        <div className="mt-4 border-t border-slate-200 pt-4 text-sm text-slate-600">
          <p>Customer: {invoice.customerName || '—'} {invoice.customerPhone ? `(${invoice.customerPhone})` : ''}</p>
          <p className="mt-1">Payment: {invoice.paymentMethod}</p>
          {invoice.voidedAt && <p className="mt-1 text-rose-600">Voided: {formatDateTime(invoice.voidedAt)}</p>}
        </div>

        <div className="mt-6 overflow-x-auto rounded-lg border border-slate-200">
          <Table dense>
            <TableHead>
              <Th>Product</Th>
              <Th>SKU</Th>
              <Th>HSN</Th>
              <Th align="right">Unit Price</Th>
              <Th align="right">GST %</Th>
              <Th align="right">Qty</Th>
              <Th align="right">Line Total</Th>
              <Th align="right">Returnable</Th>
              {canReturn && <Th align="right">Return Qty</Th>}
            </TableHead>
            <tbody>
              {invoice.items.map((item) => {
                const remaining = item.quantity - (returnedByItemId[item.id] || 0)
                return (
                  <Tr key={item.id}>
                    <Td className="font-medium text-slate-900">{item.productName}</Td>
                    <Td className="text-slate-500">{item.sku}</Td>
                    <Td className="text-slate-500">{item.hsnCode || '—'}</Td>
                    <Td align="right" className="tabular-nums">{formatCurrency(item.unitPrice)}</Td>
                    <Td align="right" className="tabular-nums">{item.gstRate}%</Td>
                    <Td align="right" className="tabular-nums">{item.quantity}</Td>
                    <Td align="right" className="tabular-nums">{formatCurrency(item.lineTotal)}</Td>
                    <Td align="right" className="tabular-nums text-slate-500">{remaining}</Td>
                    {canReturn && (
                      <Td align="right">
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
                      </Td>
                    )}
                  </Tr>
                )
              })}
            </tbody>
          </Table>
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

        <ErrorText className="mt-4">{error}</ErrorText>

        <div className="mt-6 flex items-center gap-3 border-t border-slate-200 pt-6">
          <Link to="/invoices" onClick={handleBackClick} className="text-sm font-medium text-slate-600 hover:text-slate-900">← Invoices</Link>
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