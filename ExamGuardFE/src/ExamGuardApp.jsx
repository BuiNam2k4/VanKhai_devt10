import { useEffect, useState } from 'react'
import { api, clearSession, getSession, saveSession } from './api'
import AuthScreen from './AuthScreen'
import AdminDashboard from './AdminDashboard'
import './management.css'

const roleLabels = { ADMIN: 'Quản trị viên', TEACHER: 'Giảng viên', STUDENT: 'Sinh viên' }

export default function ExamGuardApp() {
  const [session, setSession] = useState(getSession)
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!session)
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    api('/auth/me', { token: session.token, signal: controller.signal })
      .then(setUser)
      .catch(() => logout('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'))
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [session])

  function loggedIn(result) {
    saveSession(result)
    setSession({ token: result.token, expiresAt: result.expiresAt })
    setUser(result.user)
    setMessage('')
  }

  function logout(nextMessage = '') {
    clearSession()
    setSession(null)
    setUser(null)
    setMessage(nextMessage)
  }

  if (loading) return <div className="center-state">Đang xác thực tài khoản…</div>
  if (!user) return <AuthScreen onSuccess={loggedIn} initialMessage={message} />

  return <div className="app-shell">
    <header className="topbar">
      <div className="brand"><span className="brand-mark">E</span><span>ExamGuard</span></div>
      <div className="account"><div><strong>{user.fullName}</strong><small>{user.roles.map(role => roleLabels[role]).join(', ')}</small></div><button className="ghost" onClick={() => logout()}>Đăng xuất</button></div>
    </header>
    {user.roles.includes('ADMIN')
      ? <AdminDashboard token={session.token} />
      : <main className="simple-dashboard"><span className="eyebrow">EXAMGUARD</span><h1>Xin chào, {user.fullName}</h1><p>Tài khoản của bạn đã đăng nhập với vai trò {user.roles.map(role => roleLabels[role]).join(', ')}.</p><div className="empty-panel">Các chức năng nghiệp vụ dành cho vai trò này sẽ được bổ sung ở các task tiếp theo.</div></main>}
  </div>
}
