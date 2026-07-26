import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import { withMinDelay } from '../lib/withMinDelay'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'
import { useToast } from '../hooks/useToast'
import { useConfirm } from '../hooks/useConfirm'

export default function StaffForm() {
  const navigate = useNavigate()
  const showToast = useToast()
  const confirm = useConfirm()

  useEffect(() => {
    if (getRole() !== 'OWNER') navigate('/')   // UX guard only — the backend is the real gate
  }, [navigate])

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

  async function handleBackClick(e) {
    if (!email && !password) return   // let the Link navigate normally
    e.preventDefault()
    const ok = await confirm({
      title: 'Discard changes?',
      message: 'You have unsaved changes that will be lost.',
      confirmLabel: 'Discard',
      danger: true,
    })
    if (ok) navigate('/')
  }

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

          {error && <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

          <div className="flex items-center gap-3 pt-2">
            <Button type="submit" loading={isSubmitting}>Create account</Button>
            <Link to="/" onClick={handleBackClick} className="text-sm font-medium text-slate-600 hover:text-slate-900">Back</Link>
          </div>
        </form>
      </Card>
    </div>
  )
}