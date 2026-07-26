import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiFetch } from '../api'
import { withMinDelay } from '../lib/withMinDelay'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'
import { useToast } from '../hooks/useToast'
import { useConfirm } from '../hooks/useConfirm'

export default function ProductForm() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const showToast = useToast()
  const confirm = useConfirm()

  const [name, setName] = useState('')
  const [sku, setSku] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState('')
  const [gstRate, setGstRate] = useState('')
  const [hsnCode, setHsnCode] = useState('')
  const [stockQuantity, setStockQuantity] = useState('')
  const [error, setError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDirty, setIsDirty] = useState(false)

  // Wraps a setter so typing in any field marks the form dirty — used only by the fields below,
  // never by the initial edit-mode data load, so loading an existing product doesn't itself count
  // as an unsaved change.
  function field(setter) {
    return (e) => {
      setter(e.target.value)
      setIsDirty(true)
    }
  }

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
    setIsSubmitting(true)
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
        await withMinDelay(apiFetch(`/api/products/${id}`, { method: 'PUT', body: JSON.stringify(body) }))
        showToast('Product updated')
      } else {
        await withMinDelay(apiFetch('/api/products', { method: 'POST', body: JSON.stringify(body) }))
        showToast('Product created')
      }
      navigate('/products')
    } catch (err) {
      setError(err.message)
      setIsSubmitting(false)
    }
  }

  async function handleCancelClick(e) {
    if (!isDirty) return   // let the Link navigate normally
    e.preventDefault()
    const ok = await confirm({
      title: 'Discard changes?',
      message: 'You have unsaved changes that will be lost.',
      confirmLabel: 'Discard',
      danger: true,
    })
    if (ok) navigate('/products')
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">
          {isEdit ? 'Edit Product' : 'Add Product'}
        </h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Name" value={name} onChange={field(setName)} required />
          <Field label="SKU" value={sku} onChange={field(setSku)} required />
          <Field label="Description" value={description} onChange={field(setDescription)} />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Price" type="number" step="0.01" min="0" value={price}
              onChange={field(setPrice)} required />
            <Field label="GST rate (%)" type="number" step="0.01" min="0" value={gstRate}
              onChange={field(setGstRate)} required />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="HSN code" value={hsnCode} onChange={field(setHsnCode)} />
            <Field label="Stock quantity" type="number" step="1" min="0" value={stockQuantity}
              onChange={field(setStockQuantity)} required />
          </div>

          {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

          <div className="flex items-center gap-3 pt-2">
            <Button type="submit" loading={isSubmitting}>{isEdit ? 'Save' : 'Create'}</Button>
            <Link to="/products" onClick={handleCancelClick} className="text-sm font-medium text-slate-600 hover:text-slate-900">
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}
