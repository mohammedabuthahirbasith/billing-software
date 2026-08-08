import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { reportLoadError } from '../lib/loadError'
import { formatCurrency } from '../lib/format'
import Card from '../components/Card'
import Button from '../components/Button'

// Validated categorical slots (blue/orange/aqua) from the design system's reference palette —
// these three specifically validate as colorblind-safe across ALL pairs, not just adjacent ones,
// which matters here since a bar chart shows every pair at once. Fixed order, not reused elsewhere.
const PAYMENT_COLORS = {
  CASH: '#2a78d6',
  CARD: '#eb6834',
  UPI: '#1baf7a',
}

function toISODate(date) {
  return date.toISOString().slice(0, 10)
}

function presetRange(preset) {
  const now = new Date()
  const to = toISODate(now)
  if (preset === 'today') return { from: to, to }
  if (preset === 'week') {
    const from = new Date(now)
    from.setDate(from.getDate() - 6)
    return { from: toISODate(from), to }
  }
  if (preset === 'month') {
    const from = new Date(now.getFullYear(), now.getMonth(), 1)
    return { from: toISODate(from), to }
  }
  return { from: to, to }
}

export default function Reports() {
  const navigate = useNavigate()

  useEffect(() => {
    if (getRole() !== 'OWNER') navigate('/')   // UX guard only — the backend is the real gate
  }, [navigate])

  const initial = presetRange('month')
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  async function loadReport(range) {
    setError(null)
    try {
      const data = await apiFetch(`/api/reports/sales?from=${range.from}&to=${range.to}&topN=10`)
      setReport(data)
    } catch (err) {
      reportLoadError(err, navigate, setError)
    }
  }

  useEffect(() => {
    apiFetch(`/api/reports/sales?from=${from}&to=${to}&topN=10`)
      .then(setReport)
      .catch((err) => reportLoadError(err, navigate, setError))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function applyPreset(preset) {
    const range = presetRange(preset)
    setFrom(range.from)
    setTo(range.to)
    loadReport(range)
  }

  function handleCustomRange(e) {
    e.preventDefault()
    loadReport({ from, to })
  }

  const maxPaymentRevenue = report
    ? Math.max(...report.byPaymentMethod.map((p) => Number(p.revenue)), 1)
    : 1

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900">Sales Reports</h1>

      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <Button type="button" variant="secondary" onClick={() => applyPreset('today')}>Today</Button>
          <Button type="button" variant="secondary" onClick={() => applyPreset('week')}>This Week</Button>
          <Button type="button" variant="secondary" onClick={() => applyPreset('month')}>This Month</Button>

          <form onSubmit={handleCustomRange} className="ml-auto flex flex-wrap items-end gap-3">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">From</span>
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30" />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">To</span>
              <input type="date" value={to} onChange={(e) => setTo(e.target.value)}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30" />
            </label>
            <Button type="submit">Apply</Button>
          </form>
        </div>
      </Card>

      {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

      {report ? (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Card>
              <p className="text-sm text-slate-500">Total Revenue</p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{formatCurrency(report.totalRevenue)}</p>
            </Card>
            <Card>
              <p className="text-sm text-slate-500">GST Collected</p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{formatCurrency(report.totalTax)}</p>
            </Card>
            <Card>
              <p className="text-sm text-slate-500">Invoices</p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{report.invoiceCount}</p>
            </Card>
            <Card>
              <p className="text-sm text-slate-500">Voided</p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-rose-600">{report.voidedCount}</p>
            </Card>
          </div>

          <Card>
            <h2 className="mb-4 text-lg font-semibold text-slate-900">By Payment Method</h2>
            <div className="space-y-3">
              {report.byPaymentMethod.map((p) => (
                <div key={p.paymentMethod} className="flex items-center gap-3">
                  <span className="w-14 text-sm font-medium text-slate-700">{p.paymentMethod}</span>
                  <div className="h-3 flex-1 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full transition-all"
                      style={{
                        width: `${(Number(p.revenue) / maxPaymentRevenue) * 100}%`,
                        backgroundColor: PAYMENT_COLORS[p.paymentMethod],
                      }}
                    />
                  </div>
                  <span className="w-28 text-right text-sm tabular-nums text-slate-600">
                    {formatCurrency(p.revenue)} <span className="text-slate-400">({p.invoiceCount})</span>
                  </span>
                </div>
              ))}
            </div>
          </Card>

          <Card className="overflow-x-auto p-0">
            <h2 className="px-4 pt-4 text-lg font-semibold text-slate-900">Top Products</h2>
            <table className="mt-3 w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <th className="px-4 py-3">Product</th>
                  <th className="px-4 py-3">SKU</th>
                  <th className="px-4 py-3 text-right">Qty Sold</th>
                  <th className="px-4 py-3 text-right">Revenue</th>
                </tr>
              </thead>
              <tbody>
                {report.topProducts.map((p) => (
                  <tr key={p.productId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{p.productName}</td>
                    <td className="px-4 py-3 text-slate-500">{p.sku}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{p.quantitySold}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(p.revenue)}</td>
                  </tr>
                ))}
                {report.topProducts.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-4 py-8 text-center text-slate-500">No sales in this range.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </Card>
        </>
      ) : !error && <p className="text-slate-500">Loading…</p>}
    </div>
  )
}
