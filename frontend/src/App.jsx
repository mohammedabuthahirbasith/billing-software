import { useEffect, useState } from 'react'

// Dev: empty -> relative "/api/..." uses the Vite proxy.
// Prod: Vercel sets this to the Render backend URL.
const API = import.meta.env.VITE_API_BASE_URL ?? ''

function App() {
  const [health, setHealth] = useState(null)
  const [pings, setPings] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch(`${API}/api/health`)
      .then((r) => r.json())
      .then(setHealth)
      .catch((err) => setError(err.message))

    fetch(`${API}/api/pings`)
      .then((r) => r.json())
      .then(setPings)
      .catch((err) => setError(err.message))
  }, [])

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>Billing Software</h1>

      <h2>Backend status</h2>
      {health ? <p>{health.status} — {health.message}</p> : <p>Checking…</p>}

      <h2>Pings from database</h2>
      {error && <p style={{ color: 'red' }}>Error: {error}</p>}
      <ul>
        {pings.map((ping) => (
          <li key={ping.id}>#{ping.id}: {ping.message} ({ping.createdAt})</li>
        ))}
      </ul>
    </div>
  )
}

export default App