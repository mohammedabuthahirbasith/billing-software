import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import { formatCurrency, formatDateTime } from '../lib/format'
import Card from '../components/Card'
import Button from '../components/Button'
import Loading from '../components/Loading'
import InvoiceStatusBadge from '../components/InvoiceStatusBadge'
import { Table, TableHead, Th, Td, Tr, EmptyRow } from '../components/Table'

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
          <Table>
            <TableHead>
              <Th>Invoice #</Th>
              <Th>Customer</Th>
              <Th align="right">Total</Th>
              <Th>Payment</Th>
              <Th>Status</Th>
              <Th>Created</Th>
              <Th />
            </TableHead>
            <tbody>
              {invoices.map((inv) => (
                <Tr key={inv.id} hover>
                  <Td className="font-medium text-slate-900">{inv.invoiceNumber}</Td>
                  <Td className="text-slate-500">{inv.customerName || '—'}</Td>
                  <Td align="right" className="tabular-nums">{formatCurrency(inv.totalAmount)}</Td>
                  <Td className="text-slate-500">{inv.paymentMethod}</Td>
                  <Td><InvoiceStatusBadge status={inv.status} /></Td>
                  <Td className="text-slate-500">{formatDateTime(inv.createdAt)}</Td>
                  <Td align="right">
                    <Link to={`/invoices/${inv.id}`} className="font-medium text-brand-600 hover:text-brand-700">
                      View
                    </Link>
                  </Td>
                </Tr>
              ))}
              {invoices.length === 0 && <EmptyRow colSpan={7}>No invoices yet.</EmptyRow>}
            </tbody>
          </Table>
        </Card>
      ) : <Loading />}
    </div>
  )
}
