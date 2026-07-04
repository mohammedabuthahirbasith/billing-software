import { useEffect, useState } from 'react'

function App() {
  const [health, setHealth] = useState(null)
  const [pings, setPings] = useState([])       // an array, starts empty
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.json())
      .then(setHealth)
      .catch((err) => setError(err.message))

    fetch('/api/pings')
      .then((r) => r.json())
      .then(setPings)
      .catch((err) => setError(err.message))
  }, [])                                        // run both once, on mount

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