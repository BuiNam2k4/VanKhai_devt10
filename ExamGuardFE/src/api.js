const sessionKey = 'examguard.session'

export function getSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(sessionKey))
    return value?.token && Date.parse(value.expiresAt) > Date.now() ? value : null
  } catch { return null }
}

export function saveSession(auth) {
  sessionStorage.setItem(sessionKey, JSON.stringify({ token: auth.token, expiresAt: auth.expiresAt }))
}

export function clearSession() { sessionStorage.removeItem(sessionKey) }

export async function api(path, { token, method = 'GET', body, signal } = {}) {
  const response = await fetch(`/api${path}`, {
    method, signal,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(payload.message || 'Không thể xử lý yêu cầu')
    error.status = response.status
    error.fields = payload.data || {}
    throw error
  }
  return payload.data
}
