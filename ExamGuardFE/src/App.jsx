import { useEffect, useRef, useState } from 'react'
import './App.css'

const roles = { ADMIN: 'Quản trị viên', TEACHER: 'Giảng viên', STUDENT: 'Sinh viên' }
const storageKey = 'examguard.session'

function readSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey))
    return value?.accessToken && Date.parse(value.expiresAt) > Date.now() ? value : null
  } catch { return null }
}

async function api(path, { token, body, signal } = {}) {
  const response = await fetch(`/api${path}`, {
    method: body ? 'POST' : 'GET', signal,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(data.message || 'Không thể xử lý yêu cầu. Vui lòng thử lại.')
    error.status = response.status
    error.fields = data.errors || {}
    throw error
  }
  return data
}

function App() {
  const [session, setSession] = useState(readSession)
  const [user, setUser] = useState(null)
  const [mode, setMode] = useState('login')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [fields, setFields] = useState({})
  const [retry, setRetry] = useState(0)
  const [area, setArea] = useState(null)
  const pendingRequest = useRef(null)

  function logout(message = '') {
    pendingRequest.current?.abort()
    pendingRequest.current = null
    setBusy(false)
    sessionStorage.removeItem(storageKey)
    setSession(null)
    setUser(null)
    setArea(null)
    setMessage(message)
  }

  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    api('/auth/me', { token: session.accessToken, signal: controller.signal })
      .then((profile) => { if (!controller.signal.aborted) { setUser(profile); setMessage('') } })
      .catch((error) => {
        if (error.name === 'AbortError') return
        if (error.status === 401) logout('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
        else setMessage('Không kết nối được máy chủ. Kiểm tra backend rồi thử lại.')
      })
    const timer = setTimeout(() => logout('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'),
      Math.max(0, Date.parse(session.expiresAt) - Date.now()))
    return () => { controller.abort(); clearTimeout(timer) }
  }, [session, retry])

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const body = Object.fromEntries(form)
    setMessage('')
    setFields({})
    if (mode === 'register' && body.password !== body.confirmPassword) {
      setFields({ confirmPassword: 'Mật khẩu xác nhận không khớp' })
      return
    }
    if (new TextEncoder().encode(body.password).length > 72) {
      setFields({ password: 'Mật khẩu không được vượt quá 72 byte UTF-8' })
      return
    }
    delete body.confirmPassword
    const controller = new AbortController()
    pendingRequest.current = controller
    setBusy(true)
    try {
      const result = await api(`/auth/${mode}`, { body, signal: controller.signal })
      if (controller.signal.aborted) return
      const value = { accessToken: result.accessToken, expiresAt: result.expiresAt }
      sessionStorage.setItem(storageKey, JSON.stringify(value))
      setSession(value)
      setUser(result.user)
    } catch (error) {
      if (error.name === 'AbortError') return
      setMessage(error instanceof TypeError ? 'Không kết nối được máy chủ. Vui lòng thử lại.' : error.message)
      setFields(error.fields || {})
    } finally {
      if (pendingRequest.current === controller) { pendingRequest.current = null; setBusy(false) }
    }
  }

  async function openArea(role) {
    const controller = new AbortController()
    pendingRequest.current = controller
    setBusy(true)
    setMessage('')
    setArea(null)
    try {
      const profile = await api(`/${role.toLowerCase()}/profile`, { token: session.accessToken, signal: controller.signal })
      if (controller.signal.aborted) return
      setUser(profile)
      setArea(role)
    } catch (error) {
      if (error.name === 'AbortError') return
      if (error.status === 401) logout('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
      else setMessage(error instanceof TypeError ? 'Không kết nối được máy chủ. Vui lòng thử lại.' : error.message)
    } finally {
      if (pendingRequest.current === controller) { pendingRequest.current = null; setBusy(false) }
    }
  }

  function input(name, label, props = {}) {
    return <label className="field" key={name} htmlFor={name}>
      <span>{label}</span>
      <input id={name} name={name} required aria-invalid={!!fields[name]} aria-describedby={fields[name] ? `${name}-error` : undefined} {...props} />
      {fields[name] && <small className="field-error" id={`${name}-error`}>{fields[name]}</small>}
    </label>
  }

  const allowedAreas = user ? Object.keys(roles).filter((role) => user.roles.includes(role) || (role === 'TEACHER' && user.roles.includes('ADMIN'))) : []

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="/" aria-label="ExamGuard trang chủ"><span className="brand-icon">E</span>ExamGuard</a><span className="topbar-note">HỆ THỐNG KIỂM TRA TRỰC TUYẾN</span></header>
    <main className={user ? 'dashboard' : 'auth-layout'}>
      {!user && <section className="intro">
        <span className="eyebrow">KHÔNG GIAN HỌC TẬP CỦA BẠN</span>
        <h1>Mỗi bài kiểm tra.<br />Một bước tiến mới.</h1>
        <p>Đăng nhập để truy cập không gian dành cho sinh viên, giảng viên và quản trị viên.</p>
        <div className="intro-note"><span className="note-mark">✓</span><div><strong>Đúng tài khoản, đúng vai trò</strong><p>Thông tin và quyền truy cập được quản lý tập trung.</p></div></div>
        <span className="intro-footer">EXAMGUARD / CỔNG TRUY CẬP</span>
      </section>}
      {session && !user ? <section className="auth-card"><h2>Xác thực tài khoản</h2><p role="status">{message || 'Đang kiểm tra phiên đăng nhập…'}</p>{message && <button onClick={() => setRetry(retry + 1)}>Thử lại</button>}<button className="secondary" onClick={() => logout()}>Về đăng nhập</button></section> : user ? <>
        <div className="dashboard-heading"><div><span className="eyebrow">TÀI KHOẢN EXAMGUARD</span><h1>Xin chào, {user.fullName}</h1><p>Bạn đã đăng nhập thành công.</p></div><button className="secondary" onClick={() => logout()}>Đăng xuất</button></div>
        {message && <div className="alert" role="alert">{message}</div>}
        <section className="profile-card"><h2>Thông tin tài khoản</h2><dl><div><dt>Tên đăng nhập</dt><dd>{user.username}</dd></div><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Vai trò</dt><dd>{user.roles.map(role => roles[role]).join(', ') || 'Chưa được cấp vai trò'}</dd></div></dl></section>
        <h2>Không gian của bạn</h2><div className="role-grid">{allowedAreas.map(role => <button className="role-card" key={role} disabled={busy} onClick={() => openArea(role)}><span>{roles[role]}</span><small>Truy cập không gian →</small></button>)}</div>
        {area && <section className="profile-card" role="status"><h2>Không gian {roles[area].toLowerCase()}</h2><p>Bạn có quyền truy cập không gian này. Các chức năng nghiệp vụ sẽ được bổ sung ở những task tiếp theo.</p></section>}
      </> : <section className="auth-card">
        <span className="eyebrow">CHÀO MỪNG ĐẾN EXAMGUARD</span><h2>{mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</h2><p className="card-description">{mode === 'login' ? 'Nhập thông tin tài khoản để tiếp tục.' : 'Đăng ký tài khoản sinh viên để bắt đầu.'}</p>
        <div className="mode-switch"><button className={mode === 'login' ? 'active' : ''} disabled={busy} onClick={() => { setMode('login'); setMessage(''); setFields({}) }}>Đăng nhập</button><button className={mode === 'register' ? 'active' : ''} disabled={busy} onClick={() => { setMode('register'); setMessage(''); setFields({}) }}>Đăng ký</button></div>
        {message && <div className="alert" role="alert">{message}</div>}
        <form key={mode} onSubmit={submit}>
          <fieldset disabled={busy}>
            {mode === 'register' && input('fullName', 'Họ và tên', { autoComplete: 'name', maxLength: 150, placeholder: 'Nguyễn Văn An' })}
            {input('username', 'Tên đăng nhập', { autoComplete: 'username', maxLength: 50, minLength: mode === 'register' ? 3 : undefined, pattern: mode === 'register' ? '[a-zA-Z0-9_]{3,50}' : undefined, title: '3–50 chữ cái, chữ số hoặc dấu gạch dưới', placeholder: 'Nhập tên đăng nhập' })}
            {mode === 'register' && input('email', 'Email', { type: 'email', autoComplete: 'email', maxLength: 254, placeholder: 'ban@example.com' })}
            {input('password', 'Mật khẩu', { type: 'password', autoComplete: mode === 'login' ? 'current-password' : 'new-password', minLength: mode === 'register' ? 8 : undefined, maxLength: 72, placeholder: mode === 'register' ? 'Tối thiểu 8 ký tự' : 'Nhập mật khẩu' })}
            {mode === 'register' && input('confirmPassword', 'Xác nhận mật khẩu', { type: 'password', autoComplete: 'new-password', maxLength: 72, placeholder: 'Nhập lại mật khẩu' })}
            <button className="submit-button" type="submit">{busy ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập →' : 'Tạo tài khoản →'}</button>
          </fieldset>
        </form>
        <p className="card-footer">{mode === 'login' ? 'Quyền truy cập được xác định theo tài khoản của bạn.' : 'Tài khoản mới có vai trò Sinh viên. Liên hệ quản trị viên nếu cần vai trò khác.'}</p>
      </section>}
    </main>
    <footer className="page-footer">ExamGuard <span>Học tập chủ động. Đánh giá minh bạch.</span></footer>
  </div>
}
export default App
