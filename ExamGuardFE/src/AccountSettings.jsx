import { useEffect, useState } from 'react'
import { api } from './api'
import { passwordChangeError } from './account-settings'
import './account-settings.css'

const roleLabels = { ADMIN: 'Quản trị viên', TEACHER: 'Giảng viên', STUDENT: 'Sinh viên' }

export default function AccountSettings({ token, user, onClose, onUpdated, onPasswordChanged }) {
  const [profile, setProfile] = useState(null)
  const [fields, setFields] = useState({ fullName: user.fullName, username: user.username, email: user.email })
  const [loadError, setLoadError] = useState('')
  const [retry, setRetry] = useState(0)
  const [pending, setPending] = useState('')
  const [profileNotice, setProfileNotice] = useState(null)
  const [validation, setValidation] = useState({})
  const [passwordError, setPasswordError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    api('/auth/me', { token, signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) { setProfile(data); setFields({ fullName: data.fullName, username: data.username, email: data.email }); setLoadError(''); onUpdated(data) } })
      .catch(error => { if (!controller.signal.aborted) setLoadError(error.message) })
    return () => controller.abort()
  }, [token, retry, onUpdated])

  async function saveProfile(event) {
    event.preventDefault()
    if (pending || !profile) return
    setPending('profile'); setProfileNotice(null); setValidation({})
    try {
      const values = Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, value.trim()]))
      const updated = await api('/auth/me', { token, method: 'PUT', body: values })
      setProfile(updated); setFields({ fullName: updated.fullName, username: updated.username, email: updated.email }); onUpdated(updated)
      setProfileNotice({ type: 'success', text: 'Đã lưu thông tin tài khoản.' })
    } catch (error) { setProfileNotice({ type: 'error', text: error.message }); setValidation(error.fields || {}) }
    finally { setPending('') }
  }

  async function changePassword(event) {
    event.preventDefault()
    if (pending || !profile) return
    const form = new FormData(event.currentTarget)
    const values = { currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword'), confirmPassword: form.get('confirmPassword') }
    const invalid = passwordChangeError(values)
    if (invalid) { setPasswordError(invalid); return }
    setPending('password'); setPasswordError('')
    try { await api('/auth/password', { token, method: 'PUT', body: values }); onPasswordChanged() }
    catch (error) { setPasswordError(error.message) }
    finally { setPending('') }
  }

  const disabled = !!pending || !profile
  return <div className="modal-backdrop" onMouseDown={event => { if (!pending && event.target === event.currentTarget) onClose() }}><section className="modal account-settings-modal" role="dialog" aria-modal="true" aria-labelledby="account-settings-title">
    <div className="modal-head"><div><span className="eyebrow">THÔNG TIN CÁ NHÂN</span><h2 id="account-settings-title">Cài đặt tài khoản</h2></div><button type="button" aria-label="Đóng cài đặt tài khoản" disabled={!!pending} onClick={onClose}>×</button></div>
    {loadError ? <div className="notice error" role="alert">{loadError}<button onClick={() => setRetry(value => value + 1)}>Thử lại</button></div> : !profile && <p>Đang tải thông tin tài khoản…</p>}
    <div className="account-settings-grid"><section><h3>Hồ sơ của bạn</h3><p className="account-settings-note">Tên đăng nhập mới sẽ dùng cho những lần đăng nhập sau.</p>
      {profileNotice && <div className={`notice ${profileNotice.type}`} role={profileNotice.type === 'error' ? 'alert' : 'status'}>{profileNotice.text}</div>}
      <form onSubmit={saveProfile}><fieldset disabled={disabled}>
        {[['fullName', 'Họ và tên', 'text', 150], ['username', 'Tên đăng nhập', 'text', 50], ['email', 'Email', 'email', 254]].map(([name, label, type, maxLength]) => <label className="field" key={name}><span>{label}</span><input aria-label={label} name={name} type={type} value={fields[name]} onChange={event => setFields({ ...fields, [name]: event.target.value })} required maxLength={maxLength} {...(name === 'username' ? { minLength: 3, pattern: '[a-zA-Z0-9_]{3,50}' } : {})} aria-invalid={!!validation[name]} />{validation[name] && <span className="field-error">{validation[name]}</span>}</label>)}
        <p className="account-settings-note">Vai trò: {(profile || user).roles.map(role => roleLabels[role]).join(', ')}. Quyền hạn và trạng thái tài khoản do quản trị viên quản lý.</p>
        <button className="primary" type="submit">{pending === 'profile' ? 'Đang lưu…' : 'Lưu thông tin'}</button>
      </fieldset></form>
    </section><section><h3>Đổi mật khẩu</h3><p className="account-settings-note">Sau khi đổi mật khẩu, bạn cần đăng nhập lại. Các phiên đăng nhập cũ sẽ mất hiệu lực.</p>
      {passwordError && <div className="notice error" role="alert">{passwordError}</div>}
      <form onSubmit={changePassword}><fieldset disabled={disabled}>
        <label className="field"><span>Mật khẩu hiện tại</span><input aria-label="Mật khẩu hiện tại" name="currentPassword" type="password" autoComplete="current-password" required maxLength={72} /></label>
        <label className="field"><span>Mật khẩu mới</span><input aria-label="Mật khẩu mới" name="newPassword" type="password" autoComplete="new-password" required minLength={8} maxLength={72} /></label>
        <label className="field"><span>Xác nhận mật khẩu mới</span><input aria-label="Xác nhận mật khẩu mới" name="confirmPassword" type="password" autoComplete="new-password" required minLength={8} maxLength={72} /></label>
        <p className="account-settings-note">Ít nhất 8 ký tự, tối đa 72 byte UTF-8. Không chia sẻ mật khẩu với người khác.</p>
        <button className="primary" type="submit">{pending === 'password' ? 'Đang đổi mật khẩu…' : 'Đổi mật khẩu'}</button>
      </fieldset></form>
    </section></div>
  </section></div>
}
