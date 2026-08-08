import Badge from './Badge'

export default function InvoiceStatusBadge({ status }) {
  return <Badge tone={status === 'VOID' ? 'danger' : 'success'}>{status}</Badge>
}
