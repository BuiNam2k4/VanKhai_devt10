import { useState } from 'react'
import { api } from './api'

export default function AuthScreen({ onSuccess, initialMessage }) {
  const [mode, setMode] = useState('login')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(initialMessage || '')
  const [errors, setErrors] = useState({})

  async function submit(event) {
    event.preventDefault()
    const values = Object.fromEntries(new FormData(event.currentTarget))
    setMessage(''); setErrors({})
    if (mode === 'register' && values.password !== values.confirmPassword) {
      setErrors({ confirmPassword: 'Mật khẩu xác nhận không khớp' }); return
    }
    delete values.confirmPassword
    setBusy(true)
    try { onSuccess(await api(`/auth/${mode === 'login' ? 'token' : 'register'}`, { method: 'POST', body: values })) }
    catch (error) { setMessage(error.message); setErrors(error.fields || {}) }
    finally { setBusy(false) }
  }

  const field = (name, label, props = {}) => <label className="field" key={name}>
    <span>{label}</span><input name={name} required aria-invalid={!!errors[name]} {...props} />
    {errors[name] && <small className="field-error">{errors[name]}</small>}
  </label>

  return <div className="auth-page">
    <section className="auth-intro"><div className="brand light"><span className="brand-mark">E</span><span>ExamGuard</span></div><div><span className="eyebrow light-text">NỀN TẢNG KIỂM TRA TRỰC TUYẾN</span><h1>Quản lý học vụ<br />rõ ràng, nhất quán.</h1><p>Đăng nhập để quản lý người dùng, môn học và lớp học trên một không gian thống nhất.</p></div><small>TECHBYTE · EXAMGUARD 2026</small></section>
    <section className="auth-main"><div className="auth-card"><span className="eyebrow">CHÀO MỪNG TRỞ LẠI</span><h2>{mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản sinh viên'}</h2><p>Nhập thông tin để tiếp tục vào hệ thống.</p>
      <div className="segmented"><button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setMessage(''); setErrors({}) }}>Đăng nhập</button><button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => { setMode('register'); setMessage(''); setErrors({}) }}>Đăng ký</button></div>
      {message && <div className="alert" role="alert">{message}</div>}
      <form key={mode} onSubmit={submit}><fieldset disabled={busy}>
        {mode === 'register' && field('fullName', 'Họ và tên', { maxLength: 150, autoComplete: 'name' })}
        {field('username', 'Tên đăng nhập', { maxLength: 50, autoComplete: 'username' })}
        {mode === 'register' && field('email', 'Email', { type: 'email', maxLength: 254, autoComplete: 'email' })}
        {field('password', 'Mật khẩu', { type: 'password', minLength: mode === 'register' ? 8 : undefined, maxLength: 72, autoComplete: mode === 'login' ? 'current-password' : 'new-password' })}
        {mode === 'register' && field('confirmPassword', 'Xác nhận mật khẩu', { type: 'password', minLength: 8, maxLength: 72, autoComplete: 'new-password' })}
        <button className="primary full" type="submit">{busy ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập →' : 'Tạo tài khoản →'}</button>
      </fieldset></form>
      <small className="form-note">Tài khoản đăng ký công khai được cấp vai trò Sinh viên.</small>
    </div></section>
  </div>
}
