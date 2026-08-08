import { isAuthError } from '../api'

// Every page's initial load shares one policy, applied here instead of being re-decided per page:
// only a 401 means "you aren't signed in" and justifies bouncing to the login screen. Anything else
// — 403, 404, a 500, the API being unreachable — is surfaced in place. Previously these load calls
// used a blanket `.catch(() => navigate('/login'))`, which threw the real error away and made a
// backend outage look exactly like a logged-out session.
export function reportLoadError(err, navigate, setError) {
  if (isAuthError(err)) {
    navigate('/login', { replace: true })
    return
  }
  console.error('Failed to load page data', err)
  setError(err.message)
}
