import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, getRole } from '../api'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'

export default function StaffForm() {
  const navigate = useNavigate()

  useEffect(() => {
    if (getRole() !== 'OWNER') navigate('/')   // UX guard only — the backend is the real gate
  }, [navigate])

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('CASHIER')
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setSuccess(false)
    try {
      await apiFetch('/api/users', {
        method: 'POST',
        body: JSON.stringify({ email, password, role }),
      })
      setSuccess(true)
      setEmail('')
      setPassword('')
      setRole('CASHIER')
    } catch (err) {
      setError(err.message)
    }
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
          {success && <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">Account created.</p>}

          <div className="flex items-center gap-3 pt-2">
            <Button type="submit">Create account</Button>
            <Link to="/" className="text-sm font-medium text-slate-600 hover:text-slate-900">Back</Link>
          </div>
        </form>
      </Card>
    </div>
  )
}