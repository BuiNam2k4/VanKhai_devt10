import { useEffect, useState } from 'react'
import { api } from './api'
import ExamQuestionsEditor from './ExamQuestionsEditor'
import './exam-management.css'

const statuses = { DRAFT: 'Nháp', PUBLISHED: 'Đã công bố', CLOSED: 'Đã đóng' }
const visibilityLabels = { AFTER_SUBMISSION: 'Sau khi nộp bài', AFTER_EXAM_END: 'Sau khi kết thúc lịch thi', HIDDEN: 'Không công bố cho sinh viên' }
const emptyFilters = { keyword: '', subjectId: '', status: '', sortBy: 'id:desc' }

export default function ExamManagement({ token, isAdmin = false }) {
  const base = isAdmin ? '/admin' : '/teacher'
  const [catalog, setCatalog] = useState(null)
  const [catalogError, setCatalogError] = useState('')
  const [filters, setFilters] = useState(emptyFilters)
  const [draft, setDraft] = useState(emptyFilters)
  const [page, setPage] = useState(1)
  const [version, setVersion] = useState(0)
  const [loaded, setLoaded] = useState(null)
  const [failure, setFailure] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editor, setEditor] = useState(null)
  const [questionEditor, setQuestionEditor] = useState(null)
  const [pending, setPending] = useState(false)
  const query = new URLSearchParams({ page, size: 10, ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')) }).toString()
  const requestKey = `${base}?${query}&refresh=${version}`
  const result = loaded?.key === requestKey ? loaded.data : null
  const error = failure?.key === requestKey ? failure.message : ''

  useEffect(() => {
    const controller = new AbortController()
    api(`${base}/exams?${query}`, { token, signal: controller.signal })
      .then(data => setLoaded({ key: requestKey, data }))
      .catch(error => { if (!controller.signal.aborted) setFailure({ key: requestKey, message: error.message }) })
    return () => controller.abort()
  }, [base, query, requestKey, token])

  useEffect(() => {
    const controller = new AbortController()
    async function all(path, collection, extra = '') {
      const items = []
      let current = 1
      let total = 1
      do {
        const data = await api(`${path}?page=${current}&size=100${extra}`, { token, signal: controller.signal })
        items.push(...data[collection]); total = data.totalPages; current++
      } while (current <= total)
      return items
    }
    Promise.all([
      all(`${base}/subjects`, 'subjects', '&sortBy=code:asc'),
      all(`${base}/classes`, 'classes', '&sortBy=code:asc'),
      isAdmin ? all('/admin/users', 'users', '&role=TEACHER&enabled=true&sortBy=fullName:asc') : Promise.resolve([]),
    ]).then(([subjects, classes, teachers]) => { if (!controller.signal.aborted) { setCatalog({ subjects, classes, teachers }); setCatalogError('') } })
      .catch(error => { if (!controller.signal.aborted) setCatalogError(error.message) })
    return () => controller.abort()
  }, [base, isAdmin, token, version])

  function refresh() { setVersion(value => value + 1) }
  async function open(item, readOnly) {
    try { setEditor({ item: await api(`${base}/exams/${item.id}`, { token }), readOnly }) }
    catch (error) { setNotice({ type: 'error', text: error.message }) }
  }
  async function save(values) {
    const item = editor.item
    await api(`${base}/exams${item ? `/${item.id}` : ''}`, { token, method: item ? 'PUT' : 'POST', body: values })
    setEditor(null); setNotice({ type: 'success', text: item ? 'Đã lưu cấu hình bài thi.' : 'Đã tạo bài thi nháp.' }); refresh()
  }
  async function openQuestions(item) {
    try {
      const [exam, data] = await Promise.all([api(`${base}/exams/${item.id}`, { token }), api(`${base}/exams/${item.id}/questions`, { token })])
      setQuestionEditor({ exam, data })
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
  }
  async function action(item, operation) {
    const prompt = operation === 'delete' ? `Xóa bài thi nháp “${item.title}”?` : operation === 'publish'
      ? `Công bố “${item.title}”? Sau khi công bố, cấu hình bài thi sẽ được khóa.` : `Đóng “${item.title}”? Sinh viên sẽ không được bắt đầu lượt mới.`
    if (!window.confirm(prompt)) return
    setPending(true)
    try {
      await api(`${base}/exams/${item.id}${operation === 'delete' ? '' : `/${operation}`}`, { token, method: operation === 'delete' ? 'DELETE' : 'POST' })
      setNotice({ type: 'success', text: operation === 'delete' ? 'Đã xóa bài thi nháp.' : operation === 'publish' ? 'Đã công bố bài thi.' : 'Đã đóng bài thi.' })
      if (operation === 'delete' && result.exams.length === 1 && page > 1) setPage(page - 1)
      else refresh()
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
    finally { setPending(false) }
  }

  return <section className="exam-management">
    <div className="page-heading"><div><span className="eyebrow">QUẢN LÝ VÀ CẤU HÌNH BÀI THI</span><h1>Quản lý bài thi</h1><p>{isAdmin ? 'Tất cả bài thi trong hệ thống' : 'Các bài thi do bạn phụ trách'}{result ? ` · ${result.totalElements} bài thi` : ''}</p></div><button className="primary" disabled={!catalog?.subjects.length || (isAdmin && !catalog?.teachers.length)} onClick={() => setEditor({ item: null, readOnly: false })}>+ Tạo bài thi</button></div>
    {notice && <div className={`notice ${notice.type}`} role="status">{notice.text}<button aria-label="Đóng thông báo" onClick={() => setNotice(null)}>×</button></div>}
    {catalogError && <div className="notice error" role="alert">Không tải được danh mục: {catalogError}<button onClick={refresh}>Thử lại</button></div>}
    {catalog && (!catalog.subjects.length || (isAdmin && !catalog.teachers.length)) && <p>Cần có môn học và giảng viên hoạt động để tạo bài thi.</p>}
    <form className="exam-filters" onSubmit={event => { event.preventDefault(); setPage(1); setFilters({ ...draft, keyword: draft.keyword.trim() }) }}>
      <label className="field"><span>Tìm bài thi</span><input value={draft.keyword} onChange={event => setDraft({ ...draft, keyword: event.target.value })} placeholder="Tiêu đề hoặc mô tả…" /></label>
      <label className="field"><span>Môn học</span><select aria-label="Môn học" value={draft.subjectId} onChange={event => setDraft({ ...draft, subjectId: event.target.value })}><option value="">Tất cả môn</option>{catalog?.subjects.map(subject => <option key={subject.id} value={subject.id}>{subject.code} · {subject.name}</option>)}</select></label>
      <label className="field"><span>Trạng thái</span><select aria-label="Trạng thái" value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value })}><option value="">Tất cả trạng thái</option>{Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="field"><span>Sắp xếp</span><select aria-label="Sắp xếp" value={draft.sortBy} onChange={event => setDraft({ ...draft, sortBy: event.target.value })}><option value="id:desc">Mới nhất</option><option value="title:asc">Tiêu đề A–Z</option><option value="startsAt:asc">Thời gian mở tăng dần</option><option value="updatedAt:desc">Vừa cập nhật</option></select></label>
      <div className="exam-filter-actions"><button className="primary">Áp dụng</button><button type="button" className="secondary" onClick={() => { setDraft(emptyFilters); setFilters(emptyFilters); setPage(1) }}>Xóa bộ lọc</button></div>
    </form>
    <div className="table-card exam-table" aria-busy={!result && !error}>
      {error ? <div className="table-state" role="alert">{error}<button className="secondary" onClick={refresh}>Thử lại</button></div> : !result ? <div className="table-state">Đang tải bài thi…</div> : !result.exams.length ? <div className="table-state">Chưa có bài thi phù hợp. Hãy tạo bài nháp hoặc thay đổi bộ lọc.</div> :
        <table><thead><tr><th>Bài thi</th><th>Lịch và thời lượng</th><th>Cấu hình</th>{isAdmin && <th>Giảng viên</th>}<th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{result.exams.map(item => <tr key={item.id}>
          <td><strong className="exam-title">{item.title}</strong><small>#{item.id} · {item.subjectCode} · {item.subjectName}</small><small>{item.classes.length} lớp · {item.questionCount} câu hỏi</small></td>
          <td><small>Mở: {formatTime(item.startsAt)}</small><small>Đóng: {formatTime(item.endsAt)}</small><strong>{item.durationMinutes} phút / lượt</strong></td>
          <td><span className="tag">Tối đa {item.maxAttempts} lượt</span><small>{visibilityLabels[item.resultVisibility]}</small></td>
          {isAdmin && <td>{item.teacherName}</td>}<td><span className={`tag exam-status-${item.status.toLowerCase()}`}>{statuses[item.status]}</span></td>
          <td className="actions"><button disabled={!catalog || pending} onClick={() => open(item, true)}>Xem</button><button disabled={pending} onClick={() => openQuestions(item)}>Câu hỏi</button>{item.status === 'DRAFT' && <><button disabled={!catalog || pending} onClick={() => open(item, false)}>Sửa</button><button disabled={pending} onClick={() => action(item, 'publish')}>Công bố</button><button className="danger" disabled={pending} onClick={() => action(item, 'delete')}>Xóa</button></>}{item.status === 'PUBLISHED' && <button className="danger" disabled={pending} onClick={() => action(item, 'close')}>Đóng</button>}</td>
        </tr>)}</tbody></table>}
    </div>
    {result && result.totalPages > 1 && <nav className="pagination" aria-label="Phân trang bài thi"><button disabled={page <= 1 || pending} onClick={() => setPage(page - 1)}>← Trước</button><span>Trang {page} / {result.totalPages}</span><button disabled={page >= result.totalPages || pending} onClick={() => setPage(page + 1)}>Sau →</button></nav>}
    {editor && catalog && <ExamEditor editor={editor} catalog={catalog} isAdmin={isAdmin} onClose={() => setEditor(null)} onSave={save} />}
    {questionEditor && <ExamQuestionsEditor {...questionEditor} token={token} isAdmin={isAdmin} onClose={() => setQuestionEditor(null)} onSaved={refresh} />}
  </section>
}

function ExamEditor({ editor, catalog, isAdmin, onClose, onSave }) {
  const item = editor.item
  const readOnly = editor.readOnly
  const [teacherId, setTeacherId] = useState(item?.teacherId?.toString() || '')
  const [classIds, setClassIds] = useState(() => item?.classes.map(value => value.id) || [])
  const [error, setError] = useState('')
  const [fields, setFields] = useState({})
  const [busy, setBusy] = useState(false)
  const availableClasses = catalog.classes.filter(value => !isAdmin || value.teacherId.toString() === teacherId)

  async function submit(event) {
    event.preventDefault(); setError(''); setFields({})
    const form = new FormData(event.currentTarget)
    const start = form.get('startsAt'); const end = form.get('endsAt')
    if (Boolean(start) !== Boolean(end) || (start && new Date(end) <= new Date(start))) { setError('Cần nhập đủ thời gian mở và đóng; thời gian đóng phải sau thời gian mở.'); return }
    const values = { subjectId: Number(form.get('subjectId')), ...(isAdmin ? { teacherId: Number(teacherId) } : {}), title: form.get('title'), description: form.get('description'), startsAt: start ? new Date(start).toISOString() : null, endsAt: end ? new Date(end).toISOString() : null, durationMinutes: Number(form.get('durationMinutes')), maxAttempts: Number(form.get('maxAttempts')), resultVisibility: form.get('resultVisibility'), shuffleQuestions: form.has('shuffleQuestions'), shuffleOptions: form.has('shuffleOptions'), classIds }
    setBusy(true)
    try { await onSave(values) }
    catch (error) { setError(error.message); setFields(error.fields || {}) }
    finally { setBusy(false) }
  }

  return <div className="modal-backdrop"><div className="modal exam-modal" role="dialog" aria-modal="true" aria-labelledby="exam-editor-title"><div className="modal-head"><div><span className="eyebrow">{readOnly ? 'CHI TIẾT' : item ? 'CẤU HÌNH BÀI NHÁP' : 'TẠO BÀI NHÁP'}</span><h2 id="exam-editor-title">{item ? `Bài thi #${item.id}` : 'Tạo bài thi'}</h2></div><button type="button" aria-label="Đóng cửa sổ bài thi" disabled={busy} onClick={onClose}>×</button></div>
    {item && <p><span className={`tag exam-status-${item.status.toLowerCase()}`}>{statuses[item.status]}</span> {item.questionCount} câu hỏi · Giảng viên: {item.teacherName}</p>}
    {error && <div className="notice error" role="alert">{error}</div>}{Object.entries(fields).map(([name, value]) => <p className="field-error" key={name}>{name}: {value}</p>)}
    <form onSubmit={submit}><fieldset disabled={busy || readOnly}>
      <label className="field"><span>Tiêu đề bài thi</span><input aria-label="Tiêu đề bài thi" name="title" defaultValue={item?.title || ''} required maxLength={200} autoFocus={!readOnly} /></label>
      <label className="field"><span>Môn học</span><select aria-label="Môn học" name="subjectId" defaultValue={item?.subjectId || ''} required><option value="" disabled>Chọn môn học</option>{catalog.subjects.map(subject => <option key={subject.id} value={subject.id}>{subject.code} · {subject.name}</option>)}</select></label>
      {isAdmin && <label className="field"><span>Giảng viên phụ trách</span><select aria-label="Giảng viên phụ trách" value={teacherId} required onChange={event => { setTeacherId(event.target.value); setClassIds([]) }}><option value="" disabled>Chọn giảng viên</option>{catalog.teachers.map(teacher => <option key={teacher.id} value={teacher.id}>{teacher.fullName} ({teacher.username})</option>)}</select></label>}
      <label className="field"><span>Mô tả</span><textarea aria-label="Mô tả" name="description" maxLength={10000} rows={3} defaultValue={item?.description || ''} /></label>
      <div className="two-cols"><label className="field"><span>Thời gian mở</span><input aria-label="Thời gian mở" name="startsAt" type="datetime-local" step="1" defaultValue={dateInput(item?.startsAt)} /></label><label className="field"><span>Thời gian đóng</span><input aria-label="Thời gian đóng" name="endsAt" type="datetime-local" step="1" defaultValue={dateInput(item?.endsAt)} /></label></div>
      <p className="exam-form-note">Giờ hiển thị theo múi giờ của thiết bị. Có thể để trống cả hai thời điểm khi lưu nháp. Thời gian đóng cũng giới hạn thời hạn của từng lượt thi.</p>
      <div className="two-cols"><label className="field"><span>Thời lượng mỗi lượt (phút)</span><input aria-label="Thời lượng mỗi lượt (phút)" name="durationMinutes" type="number" min="1" step="1" required defaultValue={item?.durationMinutes || 45} /></label><label className="field"><span>Số lần thi tối đa</span><input aria-label="Số lần thi tối đa" name="maxAttempts" type="number" min="1" step="1" required defaultValue={item?.maxAttempts || 1} /></label></div>
      <label className="field"><span>Hiển thị kết quả</span><select aria-label="Hiển thị kết quả" name="resultVisibility" defaultValue={item?.resultVisibility || 'AFTER_EXAM_END'}>{Object.entries(visibilityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <div className="exam-shuffle"><label className="check"><input type="checkbox" name="shuffleQuestions" defaultChecked={item?.shuffleQuestions || false} />Trộn thứ tự câu hỏi</label><label className="check"><input type="checkbox" name="shuffleOptions" defaultChecked={item?.shuffleOptions || false} />Trộn thứ tự đáp án</label></div>
      <section className="exam-class-picker"><h3>Lớp được giao bài</h3>{availableClasses.length ? <div className="exam-class-list">{availableClasses.map(value => <label className="check" key={value.id}><input type="checkbox" checked={classIds.includes(value.id)} onChange={event => setClassIds(event.target.checked ? [...classIds, value.id] : classIds.filter(id => id !== value.id))} aria-label={`Giao cho lớp ${value.code}`} /><span><strong>{value.code}</strong> · {value.name}</span></label>)}</div> : <p>{isAdmin && !teacherId ? 'Chọn giảng viên để xem các lớp phụ trách.' : 'Chưa có lớp thuộc giảng viên này.'}</p>}</section>
    </fieldset>{readOnly && item?.classes.length > 0 && <p className="exam-form-note">Lớp đã được giao: {item.classes.map(value => value.code).join(', ')}</p>}
    {!readOnly && <p className="exam-form-note">Bài thi được lưu ở trạng thái nháp. Cần lịch hợp lệ, lớp được giao và câu hỏi trước khi công bố.</p>}
    <div className="modal-actions"><button className="secondary" type="button" disabled={busy} onClick={onClose}>{readOnly ? 'Đóng' : 'Hủy'}</button>{!readOnly && <button className="primary" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu bài thi'}</button>}</div></form>
  </div></div>
}

function formatTime(value) { return value ? new Date(value).toLocaleString('vi-VN') : 'Chưa đặt lịch' }
function dateInput(value) {
  if (!value) return ''
  const date = new Date(value)
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
