import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch } from '../api'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'

export default function ProductForm() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [sku, setSku] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState('')
  const [gstRate, setGstRate] = useState('')
  const [hsnCode, setHsnCode] = useState('')
  const [stockQuantity, setStockQuantity] = useState('')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!isEdit) return
    apiFetch(`/api/products/${id}`)
      .then((p) => {
        setName(p.name)
        setSku(p.sku)
        setDescription(p.description ?? '')
        setPrice(String(p.price))
        setGstRate(String(p.gstRate))
        setHsnCode(p.hsnCode ?? '')
        setStockQuantity(String(p.stockQuantity))
      })
      .catch((err) => setError(err.message))
  }, [id, isEdit])

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const body = {
      name,
      sku,
      description: description || null,
      price: Number(price),
      gstRate: Number(gstRate),
      hsnCode: hsnCode || null,
      stockQuantity: parseInt(stockQuantity, 10),
    }
    try {
      if (isEdit) {
        await apiFetch(`/api/products/${id}`, { method: 'PUT', body: JSON.stringify(body) })
      } else {
        await apiFetch('/api/products', { method: 'POST', body: JSON.stringify(body) })
      }
      navigate('/products')
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">
          {isEdit ? 'Edit Product' : 'Add Product'}
        </h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
          <Field label="SKU" value={sku} onChange={(e) => setSku(e.target.value)} required />
          <Field label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Price" type="number" step="0.01" min="0" value={price}
              onChange={(e) => setPrice(e.target.value)} required />
            <Field label="GST rate (%)" type="number" step="0.01" min="0" value={gstRate}
              onChange={(e) => setGstRate(e.target.value)} required />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="HSN code" value={hsnCode} onChange={(e) => setHsnCode(e.target.value)} />
            <Field label="Stock quantity" type="number" step="1" min="0" value={stockQuantity}
              onChange={(e) => setStockQuantity(e.target.value)} required />
          </div>

          {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

          <div className="flex items-center gap-3 pt-2">
            <Button type="submit">{isEdit ? 'Save' : 'Create'}</Button>
            <Link to="/products" className="text-sm font-medium text-slate-600 hover:text-slate-900">
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}
