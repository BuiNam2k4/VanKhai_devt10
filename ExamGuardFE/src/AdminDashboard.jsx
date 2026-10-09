import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

const tabs = {
  users: { label: 'Người dùng', title: 'Quản lý người dùng', collection: 'users', sort: 'id:desc' },
  subjects: { label: 'Môn học', title: 'Quản lý môn học', collection: 'subjects', sort: 'code:asc' },
  classes: { label: 'Lớp học', title: 'Quản lý lớp học', collection: 'classes', sort: 'code:asc' },
}
const roleLabels = { ADMIN: 'Quản trị viên', TEACHER: 'Giảng viên', STUDENT: 'Sinh viên' }

export default function AdminDashboard({ token }) {
  const [tab, setTab] = useState('users')
  const [keyword, setKeyword] = useState('')
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notice, setNotice] = useState(null)
  const [editor, setEditor] = useState(null)
  const [teachers, setTeachers] = useState([])
  const [memberClass, setMemberClass] = useState(null)

  const load = useCallback(async () => {
    try {
      const query = new URLSearchParams({ page, size: 10, sortBy: tabs[tab].sort })
      if (appliedKeyword) query.set('keyword', appliedKeyword)
      setResult(await api(`/admin/${tab}?${query}`, { token }))
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
    finally { setLoading(false) }
  }, [tab, page, appliedKeyword, token])

  // The effect intentionally refreshes server-backed rows when paging or filters change.
  // oxlint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [load])
  useEffect(() => {
    api('/admin/users?role=TEACHER&page=1&size=100&sortBy=fullName:asc', { token })
      .then(data => setTeachers(data.users)).catch(() => setTeachers([]))
  }, [token])

  function switchTab(next) {
    setTab(next); setKeyword(''); setAppliedKeyword(''); setPage(1); setResult(null); setMemberClass(null)
  }

  async function remove(item) {
    if (!window.confirm(`Xác nhận xóa “${item.fullName || item.name}”?`)) return
    try {
      await api(`/admin/${tab}/${item.id}`, { token, method: 'DELETE' })
      setNotice({ type: 'success', text: 'Đã xóa dữ liệu.' }); load()
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
  }

  async function save(values) {
    const isEdit = !!editor.item
    try {
      await api(`/admin/${editor.type}${isEdit ? `/${editor.item.id}` : ''}`, {
        token, method: isEdit ? 'PUT' : 'POST', body: values,
      })
      setEditor(null); setNotice({ type: 'success', text: isEdit ? 'Cập nhật thành công.' : 'Tạo mới thành công.' })
      if (editor.type === 'users') api('/admin/users?role=TEACHER&page=1&size=100&sortBy=fullName:asc', { token }).then(data => setTeachers(data.users))
      load()
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
  }

  const items = result?.[tabs[tab].collection] || []
  return <div className="admin-layout">
    <aside className="sidebar"><div><span className="side-label">ĐIỀU HÀNH</span>{Object.entries(tabs).map(([key, value]) => <button key={key} className={tab === key ? 'active' : ''} onClick={() => switchTab(key)}><span className="nav-dot" />{value.label}</button>)}</div><p>Dữ liệu được phân trang và tìm kiếm trực tiếp từ máy chủ.</p></aside>
    <main className="workspace">
      {memberClass ? <MembersPanel token={token} schoolClass={memberClass} onBack={() => { setMemberClass(null); load() }} /> : <>
        <div className="page-heading"><div><span className="eyebrow">QUẢN TRỊ HỌC VỤ</span><h1>{tabs[tab].title}</h1><p>{result ? `${result.totalElements} bản ghi` : 'Đang tải dữ liệu…'}</p></div><button className="primary" onClick={() => setEditor({ type: tab, item: null })}>+ Thêm mới</button></div>
        {notice && <div className={`notice ${notice.type}`} role="status">{notice.text}<button aria-label="Đóng" onClick={() => setNotice(null)}>×</button></div>}
        <form className="toolbar" onSubmit={event => { event.preventDefault(); setPage(1); setAppliedKeyword(keyword.trim()) }}><label><span>Tìm kiếm</span><input value={keyword} onChange={event => setKeyword(event.target.value)} placeholder={`Tìm trong ${tabs[tab].label.toLowerCase()}…`} /></label><button className="secondary" type="submit">Tìm kiếm</button>{appliedKeyword && <button className="text-button" type="button" onClick={() => { setKeyword(''); setAppliedKeyword(''); setPage(1) }}>Xóa bộ lọc</button>}</form>
        <section className="table-card">{loading ? <div className="table-state">Đang tải…</div> : items.length === 0 ? <div className="table-state">Không có dữ liệu phù hợp.</div> : <DataTable type={tab} items={items} onEdit={item => setEditor({ type: tab, item })} onDelete={remove} onMembers={setMemberClass} />}</section>
        {result && <Pagination page={page} totalPages={result.totalPages} onChange={setPage} />}
      </>}
    </main>
    {editor && <EditorModal editor={editor} teachers={teachers} onClose={() => setEditor(null)} onSave={save} />}
  </div>
}

function DataTable({ type, items, onEdit, onDelete, onMembers }) {
  if (type === 'users') return <table><thead><tr><th>Người dùng</th><th>Vai trò</th><th>Trạng thái</th><th /></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.fullName}</strong><small>{item.username} · {item.email}</small></td><td>{item.roles.map(role => <span className="tag" key={role}>{roleLabels[role]}</span>)}</td><td><span className={`status ${item.enabled ? 'enabled' : 'disabled'}`}>{item.enabled ? 'Hoạt động' : 'Đã khóa'}</span></td><td className="actions"><button onClick={() => onEdit(item)}>Sửa</button><button className="danger" onClick={() => onDelete(item)}>Xóa</button></td></tr>)}</tbody></table>
  if (type === 'subjects') return <table><thead><tr><th>Mã môn</th><th>Tên môn học</th><th>Mô tả</th><th /></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><span className="code">{item.code}</span></td><td><strong>{item.name}</strong></td><td className="description">{item.description || '—'}</td><td className="actions"><button onClick={() => onEdit(item)}>Sửa</button><button className="danger" onClick={() => onDelete(item)}>Xóa</button></td></tr>)}</tbody></table>
  return <table><thead><tr><th>Lớp học</th><th>Giảng viên</th><th>Sinh viên</th><th /></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.name}</strong><small>{item.code}</small></td><td>{item.teacherName}</td><td>{item.studentCount}</td><td className="actions"><button onClick={() => onMembers(item)}>Sinh viên</button><button onClick={() => onEdit(item)}>Sửa</button><button className="danger" onClick={() => onDelete(item)}>Xóa</button></td></tr>)}</tbody></table>
}

function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null
  return <nav className="pagination" aria-label="Phân trang"><button disabled={page <= 1} onClick={() => onChange(page - 1)}>← Trước</button><span>Trang {page} / {totalPages}</span><button disabled={page >= totalPages} onClick={() => onChange(page + 1)}>Sau →</button></nav>
}

function EditorModal({ editor, teachers, onClose, onSave }) {
  const item = editor.item || {}
  const isEdit = !!editor.item
  function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    let values
    if (editor.type === 'users') {
      values = { fullName: form.get('fullName'), username: form.get('username'), email: form.get('email'), roles: form.getAll('roles'), enabled: form.has('enabled') }
      if (form.get('password')) values.password = form.get('password')
    } else if (editor.type === 'subjects') {
      values = { code: form.get('code'), name: form.get('name'), description: form.get('description') }
    } else values = { code: form.get('code'), name: form.get('name'), teacherId: Number(form.get('teacherId')) }
    onSave(values)
  }
  return <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}><div className="modal" role="dialog" aria-modal="true"><div className="modal-head"><div><span className="eyebrow">{isEdit ? 'CHỈNH SỬA' : 'TẠO MỚI'}</span><h2>{tabs[editor.type].label}</h2></div><button aria-label="Đóng" onClick={onClose}>×</button></div><form onSubmit={submit}><fieldset>
    {editor.type === 'users' && <><FormField name="fullName" label="Họ và tên" value={item.fullName} maxLength="150" /><div className="two-cols"><FormField name="username" label="Tên đăng nhập" value={item.username} maxLength="50" /><FormField name="email" label="Email" value={item.email} type="email" maxLength="254" /></div><FormField name="password" label={isEdit ? 'Mật khẩu mới (để trống nếu giữ nguyên)' : 'Mật khẩu'} type="password" required={!isEdit} minLength="8" maxLength="72" /><div className="role-options"><span>Vai trò</span>{Object.keys(roleLabels).map(role => <label key={role}><input type="checkbox" name="roles" value={role} defaultChecked={item.roles?.includes(role) || (!isEdit && role === 'STUDENT')} />{roleLabels[role]}</label>)}</div><label className="check"><input type="checkbox" name="enabled" defaultChecked={isEdit ? item.enabled : true} />Tài khoản hoạt động</label></>}
    {editor.type === 'subjects' && <><FormField name="code" label="Mã môn" value={item.code} maxLength="30" /><FormField name="name" label="Tên môn học" value={item.name} maxLength="150" /><label className="field"><span>Mô tả</span><textarea name="description" defaultValue={item.description || ''} maxLength="2000" rows="4" /></label></>}
    {editor.type === 'classes' && <><FormField name="code" label="Mã lớp" value={item.code} maxLength="30" /><FormField name="name" label="Tên lớp" value={item.name} maxLength="150" /><label className="field"><span>Giảng viên phụ trách</span><select name="teacherId" required defaultValue={item.teacherId || ''}><option value="" disabled>Chọn giảng viên</option>{teachers.map(teacher => <option key={teacher.id} value={teacher.id}>{teacher.fullName} ({teacher.username})</option>)}</select></label></>}
    <div className="modal-actions"><button type="button" className="secondary" onClick={onClose}>Hủy</button><button className="primary" type="submit">{isEdit ? 'Lưu thay đổi' : 'Tạo mới'}</button></div>
  </fieldset></form></div></div>
}

function FormField({ name, label, value = '', ...props }) {
  return <label className="field"><span>{label}</span><input name={name} defaultValue={value} required {...props} /></label>
}

function MembersPanel({ token, schoolClass, onBack }) {
  const [members, setMembers] = useState(null)
  const [candidates, setCandidates] = useState([])
  const [keyword, setKeyword] = useState('')
  const [notice, setNotice] = useState('')

  const loadMembers = useCallback(() => api(`/admin/classes/${schoolClass.id}/students?page=1&size=100&sortBy=fullName:asc`, { token }).then(setMembers), [schoolClass.id, token])
  useEffect(() => { loadMembers() }, [loadMembers])

  async function search(event) {
    event.preventDefault()
    const query = new URLSearchParams({ role: 'STUDENT', page: 1, size: 100, sortBy: 'fullName:asc' })
    if (keyword.trim()) query.set('keyword', keyword.trim())
    try { setCandidates((await api(`/admin/users?${query}`, { token })).users) }
    catch (error) { setNotice(error.message) }
  }

  async function change(studentId, method) {
    try {
      await api(`/admin/classes/${schoolClass.id}/students/${studentId}`, { token, method })
      setNotice(method === 'PUT' ? 'Đã thêm sinh viên vào lớp.' : 'Đã xóa sinh viên khỏi lớp.')
      await loadMembers()
    } catch (error) { setNotice(error.message) }
  }

  const memberIds = new Set((members?.users || []).map(item => item.id))
  return <><button className="back-button" onClick={onBack}>← Quay lại danh sách lớp</button><div className="page-heading"><div><span className="eyebrow">THÀNH VIÊN LỚP</span><h1>{schoolClass.name}</h1><p>{schoolClass.code} · {members?.totalElements || 0} sinh viên</p></div></div>{notice && <div className="notice success">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    <div className="member-grid"><section className="table-card"><div className="section-head"><h2>Sinh viên trong lớp</h2></div>{!members ? <div className="table-state">Đang tải…</div> : members.users.length === 0 ? <div className="table-state">Lớp chưa có sinh viên.</div> : <ul className="people-list">{members.users.map(student => <li key={student.id}><div><strong>{student.fullName}</strong><small>{student.username} · {student.email}</small></div><button className="danger" onClick={() => change(student.id, 'DELETE')}>Xóa</button></li>)}</ul>}</section>
    <section className="table-card"><div className="section-head"><h2>Thêm sinh viên</h2><form onSubmit={search}><input value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Tên, username hoặc email" /><button className="secondary">Tìm</button></form></div>{candidates.length === 0 ? <div className="table-state">Tìm sinh viên để thêm vào lớp.</div> : <ul className="people-list">{candidates.map(student => <li key={student.id}><div><strong>{student.fullName}</strong><small>{student.username}</small></div><button disabled={memberIds.has(student.id)} onClick={() => change(student.id, 'PUT')}>{memberIds.has(student.id) ? 'Đã có' : 'Thêm'}</button></li>)}</ul>}</section></div>
  </>
}
