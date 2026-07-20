import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'

export default function Register() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    try {
      await apiFetch('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      navigate('/login')
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <Card className="w-full max-w-sm">
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">Register</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Email" type="email" value={email}
            onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          <Field label="Password" type="password" value={password} placeholder="Min 8 characters"
            onChange={(e) => setPassword(e.target.value)} required autoComplete="new-password" />
          {error && (
            <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>
          )}
          <Button type="submit" className="w-full">Create account</Button>
        </form>
        <p className="mt-6 text-center text-sm text-slate-600">
          Have an account? <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">Log in</Link>
        </p>
      </Card>
    </div>
  )
}
