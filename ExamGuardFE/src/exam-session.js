export function getDeviceId() {
  try {
    const key = 'examguard.deviceId'
    const saved = localStorage.getItem(key)
    if (saved && /^[A-Za-z0-9_-]{8,100}$/.test(saved)) return saved
    const id = crypto.randomUUID()
    localStorage.setItem(key, id)
    return id
  } catch { throw new Error('Không thể lưu mã thiết bị. Vui lòng cho phép lưu trữ trên trình duyệt rồi thử lại.') }
}

export function remainingSeconds(anchor, monotonicNow) {
  const elapsed = Math.max(0, monotonicNow - anchor.receivedAt)
  return Math.max(0, Math.ceil((anchor.expiresAt - anchor.serverTime - elapsed) / 1000))
}

export function countdown(seconds) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor(seconds % 3600 / 60)
  const rest = seconds % 60
  const pad = value => String(value).padStart(2, '0')
  return `${hours ? `${pad(hours)}:` : ''}${pad(minutes)}:${pad(rest)}`
}
