const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export function getToken() { return localStorage.getItem('token') }
export function setToken(t) { localStorage.setItem('token', t) }
export function clearToken() { localStorage.removeItem('token') }

export function getRole() { return localStorage.getItem('role') }
export function setRole(r) { localStorage.setItem('role', r) }
export function clearRole() { localStorage.removeItem('role') }

export async function apiFetch(path, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (!res.ok) {
    const text = await res.text()
    let message = text
    try {
      const parsed = JSON.parse(text)
      if (parsed.message) message = parsed.message
    } catch {
      // not JSON — use raw text as-is
    }

    // A 401 always means "this token is no longer good" (missing/expired/invalid) — clear it
    // either way. But the MESSAGE shown to the user still comes from the response body above,
    // not a hardcoded string, so a login failure surfaces its real "Invalid email or password"
    // instead of a generic "Unauthorized" that only makes sense for an expired session.
    if (res.status === 401) {
      clearToken()
      clearRole()
    }

    throw new Error(message || `Request failed: ${res.status}`)
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}