import { useEffect, useState } from 'react'

function App() {
  // A piece of state to hold the API response. Starts null (no data yet).
  const [health, setHealth] = useState(null)
  // A piece of state to hold an error message, if the call fails.
  const [error, setError] = useState(null)

  // useEffect runs AFTER the component first appears on screen.
  useEffect(() => {
    fetch('/api/health')                        // call the backend (via the proxy)
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        return response.json()                  // parse the JSON body
      })
      .then((data) => setHealth(data))          // store it -> triggers a re-render
      .catch((err) => setError(err.message))    // or capture the error
  }, [])                                         // [] = run this once, on mount

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>Billing Software</h1>
      <h2>Backend status</h2>

      {error && <p style={{ color: 'red' }}>Error: {error}</p>}
      {!health && !error && <p>Checking…</p>}
      {health && (
        <ul>
          <li>Status: {health.status}</li>
          <li>Message: {health.message}</li>
          <li>Checked at: {health.timestamp}</li>
        </ul>
      )}
    </div>
  )
}

export default App