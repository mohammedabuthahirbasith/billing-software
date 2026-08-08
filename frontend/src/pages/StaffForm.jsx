import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../api'
import { withMinDelay } from '../lib/withMinDelay'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'
import ErrorText from '../components/ErrorText'
import { useToast } from '../hooks/useToast'
import { useDiscardGuard } from '../hooks/useDiscardGuard'
import { useRequireOwner } from '../hooks/useRequireOwner'

export default function StaffForm() {
  const showToast = useToast()
  useRequireOwner()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('CASHIER')
  const [error, setError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await withMinDelay(apiFetch('/api/users', {
        method: 'POST',
        body: JSON.stringify({ email, password, role }),
      }))
      showToast('Staff account created')
      setEmail('')
      setPassword('')
      setRole('CASHIER')
    } catch (err) {
      setError(err.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleBackClick = useDiscardGuard({ isDirty: Boolean(email || password), to: '/' })

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">Add Staff</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Email" type="email" value={email}
            onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          <Field label="Password" type="password" value={password} placeholder="Min 8 characters"
            onChange={(e) => setPassword(e.target.value)} required autoComplete="new-password" />
          <Field as="select" label="Role" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="CASHIER">Cashier</option>
            <option value="OWNER">Owner</option>
          </Field>

          <ErrorText>{error}</ErrorText>

          <div className="flex items-center gap-3 pt-2">
            <Button type="submit" loading={isSubmitting}>Create account</Button>
            <Link to="/" onClick={handleBackClick} className="text-sm font-medium text-slate-600 hover:text-slate-900">Back</Link>
          </div>
        </form>
      </Card>
    </div>
  )
}