import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, setRole, setToken } from '../api'
import Card from '../components/Card'
import Field from '../components/Field'
import Button from '../components/Button'

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    try {
      const data = await apiFetch('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      setToken(data.token)   // save the JWT
      setRole(data.role)     // used to conditionally show OWNER-only UI
      navigate('/')          // go to the protected home
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <Card className="w-full max-w-sm">
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">Log in</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Email" type="email" value={email}
            onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          <Field label="Password" type="password" value={password}
            onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
          {error && (
            <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>
          )}
          <Button type="submit" className="w-full">Log in</Button>
        </form>
        <p className="mt-6 text-center text-sm text-slate-600">
          No account? <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">Register</Link>
        </p>
      </Card>
    </div>
  )
}
