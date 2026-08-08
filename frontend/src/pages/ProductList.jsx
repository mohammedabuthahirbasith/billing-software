import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { withMinDelay } from '../lib/withMinDelay'
import { formatCurrency } from '../lib/format'
import Card from '../components/Card'
import Button from '../components/Button'
import ErrorText from '../components/ErrorText'
import Loading from '../components/Loading'
import { Table, TableHead, Th, Td, Tr, EmptyRow } from '../components/Table'
import { useToast } from '../hooks/useToast'
import { useConfirm } from '../hooks/useConfirm'

export default function ProductList() {
  const [products, setProducts] = useState(null)
  const [error, setError] = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const navigate = useNavigate()
  const showToast = useToast()
  const confirm = useConfirm()
  const isOwner = getRole() === 'OWNER'

  useEffect(() => {
    apiFetch('/api/products')
      .then(setProducts)
      .catch(() => navigate('/login'))
  }, [navigate])

  async function handleDelete(id, name) {
    const ok = await confirm({
      title: 'Delete this product?',
      message: `"${name}" will be permanently removed. This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    })
    if (!ok) return
    setError(null)
    setDeletingId(id)
    try {
      await withMinDelay(apiFetch(`/api/products/${id}`, { method: 'DELETE' }))
      setProducts((prev) => prev.filter((p) => p.id !== id))
      showToast('Product deleted')
    } catch (err) {
      setError(err.message)
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Products</h1>
        {isOwner && <Link to="/products/new"><Button>Add product</Button></Link>}
      </div>

      <ErrorText>{error}</ErrorText>

      {products ? (
        <Card className="overflow-x-auto p-0">
          <Table>
            <TableHead>
              <Th>Name</Th>
              <Th>SKU</Th>
              <Th align="right">Price</Th>
              <Th align="right">GST %</Th>
              <Th align="right">Stock</Th>
              {isOwner && <Th />}
            </TableHead>
            <tbody>
              {products.map((p) => (
                <Tr key={p.id} hover>
                  <Td className="font-medium text-slate-900">{p.name}</Td>
                  <Td className="text-slate-500">{p.sku}</Td>
                  <Td align="right" className="tabular-nums">{formatCurrency(p.price)}</Td>
                  <Td align="right" className="tabular-nums">{p.gstRate}%</Td>
                  <Td align="right" className="tabular-nums">{p.stockQuantity}</Td>
                  {isOwner && (
                    <Td align="right">
                      <div className="flex justify-end gap-3">
                        <Link to={`/products/${p.id}/edit`} className="font-medium text-brand-600 hover:text-brand-700">
                          Edit
                        </Link>
                        <button onClick={() => handleDelete(p.id, p.name)} disabled={deletingId === p.id}
                          className="font-medium text-rose-600 hover:text-rose-700 disabled:cursor-not-allowed disabled:opacity-50">
                          {deletingId === p.id ? 'Deleting…' : 'Delete'}
                        </button>
                      </div>
                    </Td>
                  )}
                </Tr>
              ))}
              {products.length === 0 && (
                <EmptyRow colSpan={isOwner ? 6 : 5}>No products yet.</EmptyRow>
              )}
            </tbody>
          </Table>
        </Card>
      ) : <Loading />}
    </div>
  )
}
