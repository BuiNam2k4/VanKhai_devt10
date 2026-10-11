import { useEffect, useState } from 'react'
import { api } from './api'
import './question-bank.css'

const difficulties = { EASY: 'Dễ', MEDIUM: 'Trung bình', HARD: 'Khó' }
const types = { SINGLE_CHOICE: 'Trắc nghiệm một đáp án', ESSAY: 'Tự luận' }
const emptyFilters = { keyword: '', subjectId: '', difficulty: '', type: '', active: '', sortBy: 'id:desc' }

export default function QuestionBank({ token, isAdmin = false }) {
  const base = isAdmin ? '/admin' : '/teacher'
  const [subjects, setSubjects] = useState([])
  const [catalogError, setCatalogError] = useState('')
  const [filters, setFilters] = useState(emptyFilters)
  const [draft, setDraft] = useState(emptyFilters)
  const [page, setPage] = useState(1)
  const [version, setVersion] = useState(0)
  const [loaded, setLoaded] = useState(null)
  const [failure, setFailure] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editor, setEditor] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const query = new URLSearchParams({ page, size: 10, ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')) }).toString()
  const requestKey = `${base}?${query}&refresh=${version}`
  const result = loaded?.key === requestKey ? loaded.data : null
  const error = failure?.key === requestKey ? failure.message : ''
  const loading = !result && !error

  useEffect(() => {
    const controller = new AbortController()
    api(`${base}/questions?${query}`, { token, signal: controller.signal })
      .then(data => setLoaded({ key: requestKey, data }))
      .catch(error => { if (!controller.signal.aborted) setFailure({ key: requestKey, message: error.message }) })
    return () => controller.abort()
  }, [base, query, requestKey, token])

  useEffect(() => {
    const controller = new AbortController()
    async function loadSubjects() {
      const all = []
      let current = 1
      let total = 1
      do {
        const data = await api(`${base}/subjects?page=${current}&size=100&sortBy=code:asc`, { token, signal: controller.signal })
        all.push(...data.subjects)
        total = data.totalPages
        current++
      } while (current <= total)
      if (!controller.signal.aborted) { setSubjects(all); setCatalogError('') }
    }
    loadSubjects().catch(error => { if (!controller.signal.aborted) setCatalogError(error.message) })
    return () => controller.abort()
  }, [base, token, version])

  function refresh() { setVersion(value => value + 1) }
  function filter(event) { event.preventDefault(); setPage(1); setFilters({ ...draft, keyword: draft.keyword.trim() }) }
  function clearFilters() { setDraft(emptyFilters); setFilters(emptyFilters); setPage(1) }

  async function open(item, readOnly) {
    try {
      const detail = await api(`${base}/questions/${item.id}`, { token })
      setEditor({ item: detail, readOnly })
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
  }

  async function remove(item) {
    if (!window.confirm(`Xóa câu hỏi #${item.id}? Các lựa chọn của câu hỏi cũng sẽ được xóa.`)) return
    setDeleting(item.id)
    try {
      await api(`${base}/questions/${item.id}`, { token, method: 'DELETE' })
      setNotice({ type: 'success', text: 'Đã xóa câu hỏi.' })
      if (result.questions.length === 1 && page > 1) setPage(page - 1)
      else refresh()
    } catch (error) { setNotice({ type: 'error', text: error.message }) }
    finally { setDeleting(null) }
  }

  async function save(values) {
    const item = editor.item
    await api(`${base}/questions${item ? `/${item.id}` : ''}`, { token, method: item ? 'PUT' : 'POST', body: values })
    setEditor(null)
    setNotice({ type: 'success', text: item ? 'Đã cập nhật câu hỏi.' : 'Đã tạo câu hỏi.' })
    refresh()
  }

  return <section className="question-bank">
    <div className="page-heading"><div><span className="eyebrow">NGÂN HÀNG CÂU HỎI</span><h1>Quản lý câu hỏi</h1><p>{isAdmin ? 'Tất cả câu hỏi trong hệ thống' : 'Các câu hỏi do bạn tạo'}{result ? ` · ${result.totalElements} câu hỏi` : ''}</p></div><button className="primary" disabled={!subjects.length} onClick={() => setEditor({ item: null, readOnly: false })}>+ Thêm câu hỏi</button></div>
    {notice && <div role="status" className={`notice ${notice.type}`}>{notice.text}<button aria-label="Đóng thông báo" onClick={() => setNotice(null)}>×</button></div>}
    {catalogError && <div className="notice error" role="alert">Không tải được danh mục môn học: {catalogError}<button onClick={refresh}>Thử lại</button></div>}
    {!catalogError && !subjects.length && <p className="catalog-note">Cần có môn học để tạo câu hỏi. Quản trị viên có thể thêm môn trong mục Môn học.</p>}
    <form className="question-filters" onSubmit={filter}>
      <label className="field search-field"><span>Nội dung câu hỏi</span><input value={draft.keyword} onChange={event => setDraft({ ...draft, keyword: event.target.value })} placeholder="Tìm theo nội dung…" /></label>
      <label className="field"><span>Môn học</span><select aria-label="Môn học" value={draft.subjectId} onChange={event => setDraft({ ...draft, subjectId: event.target.value })}><option value="">Tất cả môn học</option>{subjects.map(subject => <option key={subject.id} value={subject.id}>{subject.code} · {subject.name}</option>)}</select></label>
      <label className="field"><span>Độ khó</span><select aria-label="Độ khó" value={draft.difficulty} onChange={event => setDraft({ ...draft, difficulty: event.target.value })}><option value="">Tất cả độ khó</option>{Object.entries(difficulties).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="field"><span>Loại câu hỏi</span><select aria-label="Loại câu hỏi" value={draft.type} onChange={event => setDraft({ ...draft, type: event.target.value })}><option value="">Tất cả loại</option>{Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="field"><span>Trạng thái</span><select aria-label="Trạng thái" value={draft.active} onChange={event => setDraft({ ...draft, active: event.target.value })}><option value="">Tất cả trạng thái</option><option value="true">Hoạt động</option><option value="false">Ngừng sử dụng</option></select></label>
      <label className="field"><span>Sắp xếp</span><select aria-label="Sắp xếp" value={draft.sortBy} onChange={event => setDraft({ ...draft, sortBy: event.target.value })}><option value="id:desc">Mới nhất</option><option value="id:asc">Cũ nhất</option><option value="updatedAt:desc">Vừa cập nhật</option><option value="content:asc">Nội dung A–Z</option></select></label>
      <div className="filter-actions"><button className="primary">Áp dụng</button><button type="button" className="secondary" onClick={clearFilters}>Xóa bộ lọc</button></div>
    </form>
    <div className="table-card question-table" aria-busy={loading}>
      {error ? <div className="table-state" role="alert">{error}<button className="secondary" onClick={refresh}>Thử lại</button></div> : loading ? <div className="table-state">Đang tải câu hỏi…</div> : !result.questions.length ? <div className="table-state">Chưa có câu hỏi phù hợp. Hãy tạo câu hỏi hoặc thay đổi bộ lọc.</div> :
        <table><thead><tr><th>Câu hỏi</th><th>Môn học</th><th>Phân loại</th>{isAdmin && <th>Người tạo</th>}<th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{result.questions.map(item => <tr key={item.id}>
          <td><small>#{item.id}</small><strong className="question-excerpt">{item.content}</strong><small>{item.type === 'SINGLE_CHOICE' ? `${item.options.length} lựa chọn` : 'Câu trả lời tự luận'}</small></td>
          <td><span className="code">{item.subjectCode}</span><small>{item.subjectName}</small></td><td><span className={`tag difficulty-${item.difficulty.toLowerCase()}`}>{difficulties[item.difficulty]}</span><small>{types[item.type]}</small></td>
          {isAdmin && <td>{item.createdByName}</td>}<td><span className={`status ${item.active ? 'enabled' : 'disabled'}`}>{item.active ? 'Hoạt động' : 'Ngừng sử dụng'}</span></td>
          <td className="actions"><button onClick={() => open(item, true)}>Xem</button><button onClick={() => open(item, false)}>Sửa</button><button className="danger" disabled={deleting !== null} onClick={() => remove(item)}>{deleting === item.id ? 'Đang xóa…' : 'Xóa'}</button></td>
        </tr>)}</tbody></table>}
    </div>
    {result && result.totalPages > 1 && <nav className="pagination" aria-label="Phân trang câu hỏi"><button disabled={page <= 1} onClick={() => setPage(page - 1)}>← Trước</button><span>Trang {page} / {result.totalPages}</span><button disabled={page >= result.totalPages} onClick={() => setPage(page + 1)}>Sau →</button></nav>}
    {editor && <QuestionEditor editor={editor} subjects={subjects} onClose={() => setEditor(null)} onSave={save} />}
  </section>
}

function QuestionEditor({ editor, subjects, onClose, onSave }) {
  const item = editor.item
  const readOnly = editor.readOnly
  const [type, setType] = useState(item?.type || 'SINGLE_CHOICE')
  const [choices, setChoices] = useState(() => item?.options.length ? item.options.map(option => ({ ...option, key: `saved-${option.id}` })) : [newOption(true), newOption(false)])
  const [error, setError] = useState('')
  const [fields, setFields] = useState({})
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError(''); setFields({})
    if (type === 'SINGLE_CHOICE') {
      if (choices.length < 2 || choices.filter(option => option.correct).length !== 1) { setError('Cần ít nhất hai lựa chọn và đúng một đáp án đúng.'); return }
      if (new Set(choices.map(option => option.content.trim().toLowerCase())).size !== choices.length) { setError('Nội dung các lựa chọn không được trùng nhau.'); return }
    }
    const form = new FormData(event.currentTarget)
    const values = { subjectId: Number(form.get('subjectId')), content: form.get('content'), type, difficulty: form.get('difficulty'), active: form.has('active'), options: type === 'ESSAY' ? [] : choices.map(option => ({ ...(option.id ? { id: option.id } : {}), content: option.content, correct: option.correct })) }
    setBusy(true)
    try { await onSave(values) }
    catch (error) { setError(error.message); setFields(error.fields || {}) }
    finally { setBusy(false) }
  }

  return <div className="modal-backdrop"><div className="modal question-modal" role="dialog" aria-modal="true" aria-labelledby="question-editor-title"><div className="modal-head"><div><span className="eyebrow">{readOnly ? 'CHI TIẾT' : item ? 'CHỈNH SỬA' : 'TẠO MỚI'}</span><h2 id="question-editor-title">{item ? `Câu hỏi #${item.id}` : 'Thêm câu hỏi'}</h2></div><button type="button" disabled={busy} aria-label="Đóng cửa sổ câu hỏi" onClick={onClose}>×</button></div>
    {error && <div className="notice error" role="alert">{error}</div>}{Object.entries(fields).map(([name, value]) => <p key={name} className="field-error">{name}: {value}</p>)}
    <form onSubmit={submit}><fieldset disabled={busy || readOnly}>
      <label className="field"><span>Môn học</span><select aria-label="Môn học" name="subjectId" defaultValue={item?.subjectId || ''} required><option disabled value="">Chọn môn học</option>{subjects.map(subject => <option key={subject.id} value={subject.id}>{subject.code} · {subject.name}</option>)}</select></label>
      <div className="two-cols"><label className="field"><span>Loại câu hỏi</span><select aria-label="Loại câu hỏi" value={type} onChange={event => setType(event.target.value)}>{Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label className="field"><span>Độ khó</span><select aria-label="Độ khó" name="difficulty" defaultValue={item?.difficulty || 'EASY'}>{Object.entries(difficulties).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
      <label className="field"><span>Nội dung câu hỏi</span><textarea aria-label="Nội dung câu hỏi" name="content" required maxLength={10000} rows={5} defaultValue={item?.content || ''} placeholder="Nhập nội dung câu hỏi…" /></label>
      {type === 'SINGLE_CHOICE' && <section className="options-editor"><h3>Các lựa chọn</h3><p>Chọn một đáp án đúng. Thứ tự trên danh sách là thứ tự hiển thị.</p>{choices.map((option, index) => <div className="option-row" key={option.key}>
        <label className="correct-selector"><input type="radio" name="correctAnswer" checked={option.correct} onChange={() => setChoices(choices.map(choice => ({ ...choice, correct: choice.key === option.key })))} aria-label={`Đáp án ${index + 1} đúng`} /><span>{String.fromCharCode(65 + index)}</span></label>
        <textarea required rows={2} maxLength={2000} value={option.content} onChange={event => setChoices(choices.map(choice => choice.key === option.key ? { ...choice, content: event.target.value } : choice))} aria-label={`Nội dung đáp án ${index + 1}`} />
        {!readOnly && <div className="option-actions"><button type="button" disabled={index === 0} aria-label={`Đưa đáp án ${index + 1} lên`} onClick={() => { const next = [...choices]; [next[index - 1], next[index]] = [next[index], next[index - 1]]; setChoices(next) }}>↑</button><button type="button" disabled={choices.length <= 2} aria-label={`Xóa đáp án ${index + 1}`} onClick={() => setChoices(choices.filter(choice => choice.key !== option.key))}>×</button></div>}
      </div>)}{!readOnly && <button type="button" className="secondary" disabled={choices.length >= 10} onClick={() => setChoices([...choices, newOption(false)])}>+ Thêm lựa chọn</button>}</section>}
      <label className="check"><input type="checkbox" name="active" defaultChecked={item ? item.active : true} />Câu hỏi hoạt động</label>
    </fieldset><div className="modal-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>{readOnly ? 'Đóng' : 'Hủy'}</button>{!readOnly && <button className="primary" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu câu hỏi'}</button>}</div></form>
  </div></div>
}

function newOption(correct) { return { key: crypto.randomUUID(), content: '', correct } }
