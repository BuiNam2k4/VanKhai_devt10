import { useEffect, useState } from 'react'
import { api } from './api'
import './class-directory.css'

function useDirectoryPage(path, token, keyword, page, version, sortBy) {
  const [loaded, setLoaded] = useState(null)
  const [failure, setFailure] = useState(null)
  const query = new URLSearchParams({ keyword, page, size: 10, sortBy }).toString()
  const key = `${path}?${query}&refresh=${version}`
  useEffect(() => {
    const controller = new AbortController()
    api(`${path}?${query}`, { token, signal: controller.signal })
      .then(data => setLoaded({ key, data }))
      .catch(error => { if (!controller.signal.aborted) setFailure({ key, message: error.message }) })
    return () => controller.abort()
  }, [key, path, query, token])
  return { data: loaded?.key === key ? loaded.data : null, error: failure?.key === key ? failure.message : '' }
}

export default function ClassDirectory({ token, path, title, description, rosterBase }) {
  const [keyword, setKeyword] = useState('')
  const [applied, setApplied] = useState('')
  const [page, setPage] = useState(1)
  const [version, setVersion] = useState(0)
  const [selected, setSelected] = useState(null)
  const { data, error } = useDirectoryPage(path, token, applied, page, version, 'code:asc')
  if (selected) return <ClassRoster key={selected.id} token={token} schoolClass={selected} base={rosterBase} onBack={() => setSelected(null)} />
  return <section className="class-directory">
    <div className="page-heading"><div><span className="eyebrow">LỚP HỌC VÀ TÀI KHOẢN</span><h1>{title}</h1><p>{description}</p><p>{data ? `${data.totalElements} lớp học` : 'Đang tải danh sách lớp…'}</p></div><button className="secondary" onClick={() => setVersion(value => value + 1)}>Làm mới</button></div>
    <form className="toolbar" onSubmit={event => { event.preventDefault(); setPage(1); setApplied(keyword.trim()) }}><label><span>Tìm lớp học</span><input value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Mã lớp, tên lớp hoặc giảng viên" /></label><button className="secondary" type="submit">Tìm kiếm</button>{applied && <button className="text-button" type="button" onClick={() => { setKeyword(''); setApplied(''); setPage(1) }}>Xóa bộ lọc</button>}</form>
    {error ? <div className="notice error" role="alert">{error}<button onClick={() => setVersion(value => value + 1)}>Thử lại</button></div> : !data ? <div className="table-state">Đang tải lớp học…</div> : !data.classes.length ? <div className="table-card table-state">{applied ? 'Không có lớp phù hợp với tìm kiếm.' : 'Tài khoản chưa tham gia hoặc phụ trách lớp nào.'}</div> : <div className="class-directory-grid">{data.classes.map(item => <article className="class-directory-card" key={item.id}><span className="code">{item.code}</span><h2>{item.name}</h2><p>Giảng viên: <strong>{item.teacherName}</strong></p><p>{item.studentCount} sinh viên</p>{rosterBase && <button className="secondary" onClick={() => setSelected(item)}>Xem sinh viên</button>}</article>)}</div>}
    {data && <DirectoryPagination page={page} totalPages={data.totalPages} onChange={setPage} label="Phân trang lớp học" />}
  </section>
}

function ClassRoster({ token, schoolClass, base, onBack }) {
  const [keyword, setKeyword] = useState('')
  const [applied, setApplied] = useState('')
  const [page, setPage] = useState(1)
  const [version, setVersion] = useState(0)
  const { data, error } = useDirectoryPage(`${base}/${schoolClass.id}/students`, token, applied, page, version, 'username:asc')
  return <section className="class-directory">
    <button className="back-button" onClick={onBack}>← Quay lại danh sách lớp</button>
    <div className="page-heading"><div><span className="eyebrow">SINH VIÊN TRONG LỚP</span><h1>{schoolClass.code}</h1><p>{schoolClass.name} · Giảng viên: {schoolClass.teacherName}</p></div><button className="secondary" onClick={() => setVersion(value => value + 1)}>Làm mới</button></div>
    <form className="toolbar" onSubmit={event => { event.preventDefault(); setPage(1); setApplied(keyword.trim()) }}><label><span>Tìm sinh viên</span><input value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Tên đăng nhập, họ tên hoặc email" /></label><button className="secondary" type="submit">Tìm kiếm</button>{applied && <button className="text-button" type="button" onClick={() => { setKeyword(''); setApplied(''); setPage(1) }}>Xóa bộ lọc</button>}</form>
    {data && <p>{data.totalElements} sinh viên{applied ? ' phù hợp' : ' trong lớp'}</p>}
    <div className="table-card">{error ? <div className="table-state" role="alert">{error}<button className="secondary" onClick={() => setVersion(value => value + 1)}>Thử lại</button></div> : !data ? <div className="table-state">Đang tải sinh viên…</div> : !data.users.length ? <div className="table-state">{applied ? 'Không có sinh viên phù hợp.' : 'Lớp chưa có sinh viên.'}</div> : <ul className="people-list class-roster-list">{data.users.map(student => <li key={student.id}><div><strong>{student.fullName}</strong><small>Tên đăng nhập: <b>{student.username}</b></small><small>{student.email}</small></div><span className="tag">{student.enabled ? 'Hoạt động' : 'Đã khóa'}</span></li>)}</ul>}</div>
    {data && <DirectoryPagination page={page} totalPages={data.totalPages} onChange={setPage} label="Phân trang sinh viên" />}
  </section>
}

function DirectoryPagination({ page, totalPages, onChange, label }) {
  return totalPages > 1 && <nav className="pagination" aria-label={label}><button disabled={page <= 1} onClick={() => onChange(page - 1)}>← Trước</button><span>Trang {page} / {totalPages}</span><button disabled={page >= totalPages} onClick={() => onChange(page + 1)}>Sau →</button></nav>
}
