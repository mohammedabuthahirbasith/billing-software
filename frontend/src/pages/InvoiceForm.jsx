import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import { reportLoadError } from '../lib/loadError'
import { withMinDelay } from '../lib/withMinDelay'
import { formatCurrency } from '../lib/format'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'
import ErrorText from '../components/ErrorText'
import Loading from '../components/Loading'
import { Table, TableHead, Th, Td, Tr } from '../components/Table'
import { useToast } from '../hooks/useToast'
import { useDiscardGuard } from '../hooks/useDiscardGuard'

export default function InvoiceForm() {
  const [products, setProducts] = useState(null)
  const [cart, setCart] = useState([])
  const [selectedProductId, setSelectedProductId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [customerName, setCustomerName] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('CASH')
  const [barcode, setBarcode] = useState('')
  const [barcodeError, setBarcodeError] = useState(null)
  const [error, setError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const navigate = useNavigate()
  const showToast = useToast()
  const barcodeInputRef = useRef(null)

  useEffect(() => {
    apiFetch('/api/products')
      .then(setProducts)
      .catch((err) => reportLoadError(err, navigate, setError))
  }, [navigate])

  useEffect(() => {
    barcodeInputRef.current?.focus()   // ready for the next scan as soon as the page loads
  }, [])

  // Shared by both entry paths (scan and manual picker) so scanning the same item twice bumps
  // its quantity instead of creating a second, confusing row for the same product.
  function addItemToCart(product, qty) {
    setCart((prev) => {
      const existingIndex = prev.findIndex((item) => item.productId === product.id)
      if (existingIndex >= 0) {
        const updated = [...prev]
        updated[existingIndex] = { ...updated[existingIndex], quantity: updated[existingIndex].quantity + qty }
        return updated
      }
      return [...prev, { productId: product.id, name: product.name, sku: product.sku, price: product.price, quantity: qty }]
    })
  }

  function handleAddToCart() {
    setError(null)
    const product = products?.find((p) => String(p.id) === selectedProductId)
    const qty = parseInt(quantity, 10)
    if (!product) { setError('Select a product'); return }
    if (!qty || qty < 1) { setError('Quantity must be at least 1'); return }
    addItemToCart(product, qty)
    setQuantity('1')
  }

  // Real barcode scanners act as a "keyboard wedge": they type the code then an Enter keystroke
  // into whatever input has focus. No camera/scanning library needed — just capture that Enter.
  async function handleBarcodeKeyDown(e) {
    if (e.key !== 'Enter') return
    e.preventDefault()
    const sku = barcode.trim()
    setBarcode('')
    if (!sku) return
    setBarcodeError(null)
    try {
      const product = await apiFetch(`/api/products/by-sku/${encodeURIComponent(sku)}`)
      addItemToCart(product, 1)
    } catch (err) {
      setBarcodeError(err.message)
    } finally {
      barcodeInputRef.current?.focus()   // stay ready for the next scan
    }
  }

  function handleRemove(index) {
    setCart((prev) => prev.filter((_, i) => i !== index))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    if (cart.length === 0) { setError('Add at least one item'); return }
    setIsSubmitting(true)
    try {
      const invoice = await withMinDelay(apiFetch('/api/invoices', {
        method: 'POST',
        body: JSON.stringify({
          customerName: customerName || null,
          customerPhone: customerPhone || null,
          paymentMethod,
          items: cart.map(({ productId, quantity }) => ({ productId, quantity })),
        }),
      }))
      showToast('Invoice created')
      navigate(`/invoices/${invoice.id}`)
    } catch (err) {
      setError(err.message)
      setIsSubmitting(false)
    }
  }

  const estimatedSubtotal = cart.reduce((sum, item) => sum + item.price * item.quantity, 0)
  const isDirty = cart.length > 0 || customerName.trim() !== '' || customerPhone.trim() !== ''

  const handleCancelClick = useDiscardGuard({
    isDirty,
    to: '/invoices',
    title: 'Discard this invoice?',
    message: 'The items and details you entered will be lost.',
  })

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">New Invoice</h1>

        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Scan barcode</span>
          <input
            ref={barcodeInputRef}
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
            onKeyDown={handleBarcodeKeyDown}
            placeholder="Scan or type SKU, then press Enter"
            className="block w-full rounded-lg border border-brand-300 bg-brand-50/40 px-3 py-2.5
              font-mono text-base text-slate-900 shadow-sm transition-colors
              focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
          />
        </label>
        <ErrorText className="mt-2">{barcodeError}</ErrorText>

        <div className="mt-6 grid grid-cols-1 gap-4 border-t border-slate-200 pt-6 sm:grid-cols-2">
          <Field label="Customer name (optional)" value={customerName}
            onChange={(e) => setCustomerName(e.target.value)} />
          <Field label="Customer phone (optional)" value={customerPhone}
            onChange={(e) => setCustomerPhone(e.target.value)} />
          <Field as="select" label="Payment method" value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value)}>
            <option value="CASH">Cash</option>
            <option value="CARD">Card</option>
            <option value="UPI">UPI</option>
          </Field>
        </div>

        <div className="mt-6 border-t border-slate-200 pt-6">
          <p className="mb-3 text-sm font-medium text-slate-700">Or add manually</p>
          {products ? (
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="flex-1">
                <Field as="select" label="Product" value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}>
                  <option value="">Select a product…</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>{p.name} ({p.sku}) — {formatCurrency(p.price)}</option>
                  ))}
                </Field>
              </div>
              <div className="w-full sm:w-24">
                <Field label="Qty" type="number" min="1" step="1" value={quantity}
                  onChange={(e) => setQuantity(e.target.value)} />
              </div>
              <Button type="button" variant="secondary" onClick={handleAddToCart}>Add</Button>
            </div>
          ) : <p className="text-slate-500">{error ? 'Products could not be loaded.' : 'Loading products…'}</p>}
        </div>

        {cart.length > 0 && (
          <div className="mt-6 overflow-x-auto rounded-lg border border-slate-200">
            <Table dense>
              <TableHead>
                <Th>Product</Th>
                <Th>SKU</Th>
                <Th align="right">Qty</Th>
                <Th align="right">Price</Th>
                <Th />
              </TableHead>
              <tbody>
                {cart.map((item, i) => (
                  <Tr key={i}>
                    <Td className="font-medium text-slate-900">{item.name}</Td>
                    <Td className="text-slate-500">{item.sku}</Td>
                    <Td align="right" className="tabular-nums">{item.quantity}</Td>
                    <Td align="right" className="tabular-nums">{formatCurrency(item.price)}</Td>
                    <Td align="right">
                      <button type="button" onClick={() => handleRemove(i)} className="font-medium text-rose-600 hover:text-rose-700">
                        Remove
                      </button>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </div>
        )}

        {cart.length > 0 && (
          <p className="mt-3 text-right text-sm text-slate-600">
            Estimated subtotal (excl. tax): <span className="font-semibold tabular-nums">{formatCurrency(estimatedSubtotal)}</span>
          </p>
        )}

        <ErrorText className="mt-4">{error}</ErrorText>

        <div className="mt-6 flex items-center gap-3">
          <Button onClick={handleSubmit} loading={isSubmitting}>Create Invoice</Button>
          <Link to="/invoices" onClick={handleCancelClick} className="text-sm font-medium text-slate-600 hover:text-slate-900">Cancel</Link>
        </div>
      </Card>
    </div>
  )
}
