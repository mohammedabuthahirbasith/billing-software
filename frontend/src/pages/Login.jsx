import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, setToken } from '../api'

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
      navigate('/')          // go to the protected home
    } catch (err) {
      setError(`Invalid email or password${err}`)
    }
  }

  return (
    <div style={{ maxWidth: 360, margin: '4rem auto', fontFamily: 'sans-serif' }}>
      <h1>Log in</h1>
      <form onSubmit={handleSubmit}>
        <input type="email" placeholder="Email" value={email}
          onChange={(e) => setEmail(e.target.value)} required
          style={{ display: 'block', width: '100%', marginBottom: 8, padding: 8 }} />
        <input type="password" placeholder="Password" value={password}
          onChange={(e) => setPassword(e.target.value)} required
          style={{ display: 'block', width: '100%', marginBottom: 8, padding: 8 }} />
        <button type="submit" style={{ padding: 8, width: '100%' }}>Log in</button>
      </form>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      <p>No account? <Link to="/register">Register</Link></p>
    </div>
  )
}