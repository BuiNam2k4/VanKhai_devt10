import { useEffect, useState } from 'react'
import { api, clearSession, getSession, saveSession } from './api'
import AuthScreen from './AuthScreen'
import AdminDashboard from './AdminDashboard'
import TeacherDashboard from './TeacherDashboard'
import StudentDashboard from './StudentDashboard'
import AccountSettings from './AccountSettings'
import './management.css'

const roleLabels = { ADMIN: 'Quản trị viên', TEACHER: 'Giảng viên', STUDENT: 'Sinh viên' }

export default function ExamGuardApp() {
  const [session, setSession] = useState(getSession)
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!session)
  const [message, setMessage] = useState('')
  const [settingsOpen, setSettingsOpen] = useState(false)

  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    api('/auth/me', { token: session.token, signal: controller.signal })
      .then(setUser)
      .catch(error => { if (!controller.signal.aborted) { if (error.status === 0 || error.status >= 500) setMessage(error.message); else logout('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.') } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
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
    setSettingsOpen(false)
  }

  if (loading) return <div className="center-state">Đang xác thực tài khoản…</div>
  if (session && !user && message) return <div className="center-state"><div><p role="alert">{message}</p><button className="secondary" onClick={() => { setMessage(''); setLoading(true); setSession({ ...session }) }}>Thử lại</button></div></div>
  if (!user) return <AuthScreen onSuccess={loggedIn} initialMessage={message} />

  return <div className="app-shell">
    <header className="topbar">
      <div className="brand"><span className="brand-mark">E</span><span>ExamGuard</span></div>
      <div className="account"><div><strong>{user.fullName}</strong><small>{user.username} · {user.roles.map(role => roleLabels[role]).join(', ')}</small></div><div className="account-buttons"><button className="ghost" onClick={() => setSettingsOpen(true)}>Cài đặt tài khoản</button><button className="ghost" onClick={() => logout()}>Đăng xuất</button></div></div>
    </header>
    {user.roles.includes('ADMIN')
      ? <AdminDashboard token={session.token} />
      : user.roles.includes('TEACHER')
        ? <TeacherDashboard token={session.token} />
      : <StudentDashboard token={session.token} />}
    {settingsOpen && <AccountSettings token={session.token} user={user} onClose={() => setSettingsOpen(false)} onUpdated={setUser} onPasswordChanged={() => logout('Đổi mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.')} />}
  </div>
}
