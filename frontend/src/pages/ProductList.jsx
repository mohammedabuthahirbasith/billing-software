import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { formatCurrency } from '../lib/format'
import Card from '../components/Card'
import Button from '../components/Button'

export default function ProductList() {
  const [products, setProducts] = useState(null)
  const [error, setError] = useState(null)
  const navigate = useNavigate()
  const isOwner = getRole() === 'OWNER'

  useEffect(() => {
    apiFetch('/api/products')
      .then(setProducts)
      .catch(() => navigate('/login'))
  }, [navigate])

  async function handleDelete(id) {
    if (!window.confirm('Delete this product?')) return
    setError(null)
    try {
      await apiFetch(`/api/products/${id}`, { method: 'DELETE' })
      setProducts((prev) => prev.filter((p) => p.id !== id))
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Products</h1>
        {isOwner && <Link to="/products/new"><Button>Add product</Button></Link>}
      </div>

      {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

      {products ? (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">SKU</th>
                <th className="px-4 py-3 text-right">Price</th>
                <th className="px-4 py-3 text-right">GST %</th>
                <th className="px-4 py-3 text-right">Stock</th>
                {isOwner && <th className="px-4 py-3"></th>}
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-900">{p.name}</td>
                  <td className="px-4 py-3 text-slate-500">{p.sku}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(p.price)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{p.gstRate}%</td>
                  <td className="px-4 py-3 text-right tabular-nums">{p.stockQuantity}</td>
                  {isOwner && (
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-3">
                        <Link to={`/products/${p.id}/edit`} className="font-medium text-brand-600 hover:text-brand-700">
                          Edit
                        </Link>
                        <button onClick={() => handleDelete(p.id)} className="font-medium text-rose-600 hover:text-rose-700">
                          Delete
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
              {products.length === 0 && (
                <tr>
                  <td colSpan={isOwner ? 6 : 5} className="px-4 py-8 text-center text-slate-500">No products yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </Card>
      ) : <p className="text-slate-500">Loading…</p>}
    </div>
  )
}
