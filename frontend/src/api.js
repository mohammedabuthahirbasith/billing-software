const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export function getToken() { return localStorage.getItem('token') }
export function setToken(t) { localStorage.setItem('token', t) }
export function clearToken() { localStorage.removeItem('token') }

export async function apiFetch(path, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (res.status === 401) {          // token missing/expired
    clearToken()
    throw new Error('Unauthorized')
  }
  if (!res.ok) {
    throw new Error((await res.text()) || `Request failed: ${res.status}`)
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}