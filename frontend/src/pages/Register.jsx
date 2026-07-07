import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'

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
    <div style={{ maxWidth: 360, margin: '4rem auto', fontFamily: 'sans-serif' }}>
      <h1>Register</h1>
      <form onSubmit={handleSubmit}>
        <input type="email" placeholder="Email" value={email}
          onChange={(e) => setEmail(e.target.value)} required
          style={{ display: 'block', width: '100%', marginBottom: 8, padding: 8 }} />
        <input type="password" placeholder="Password (min 8 chars)" value={password}
          onChange={(e) => setPassword(e.target.value)} required
          style={{ display: 'block', width: '100%', marginBottom: 8, padding: 8 }} />
        <button type="submit" style={{ padding: 8, width: '100%' }}>Create account</button>
      </form>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      <p>Have an account? <Link to="/login">Log in</Link></p>
    </div>
  )
}