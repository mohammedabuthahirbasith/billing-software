const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export function getToken() { return localStorage.getItem('token') }
export function setToken(t) { localStorage.setItem('token', t) }
export function clearToken() { localStorage.removeItem('token') }

export function getRole() { return localStorage.getItem('role') }
export function setRole(r) { localStorage.setItem('role', r) }
export function clearRole() { localStorage.removeItem('role') }

// Every apiFetch rejection is an ApiError, so callers can branch on WHY a call failed instead of
// treating "your session expired" and "the server is down" as the same opaque failure. `status` is
// the HTTP status, or 0 when the request never produced a response (offline, DNS, CORS, abort).
export class ApiError extends Error {
  constructor(message, status, { cause } = {}) {
    super(message, { cause })
    this.name = 'ApiError'
    this.status = status
  }
}

export function isAuthError(error) {
  return error instanceof ApiError && error.status === 401
}

export async function apiFetch(path, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  let res
  try {
    res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  } catch (err) {
    // fetch only rejects on transport-level failures. Its native message ("Failed to fetch") is
    // meaningless to a cashier, so it becomes a readable one — with the original kept as `cause`.
    throw new ApiError('Network error — could not reach the server. Check your connection.', 0, { cause: err })
  }

  const text = await readBody(res)

  if (!res.ok) {
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

    throw new ApiError(message || `Request failed: ${res.status}`, res.status)
  }

  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (err) {
    // A 200 whose body isn't the JSON we expect (a proxy's HTML error page, a truncated response)
    // would otherwise surface as a bare SyntaxError with no hint of which call produced it.
    throw new ApiError(`Unexpected response from ${path} — the server did not return valid JSON.`, res.status, { cause: err })
  }
}

async function readBody(res) {
  try {
    return await res.text()
  } catch (err) {
    throw new ApiError('Connection lost while reading the server response.', res.status, { cause: err })
  }
}