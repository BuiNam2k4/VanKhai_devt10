import { useEffect, useState } from 'react'
import { api } from './api'
import './exam-questions.css'

const types = { SINGLE_CHOICE: 'Trắc nghiệm', ESSAY: 'Tự luận' }
const levels = { EASY: 'Dễ', MEDIUM: 'Trung bình', HARD: 'Khó' }
const emptyFilters = { keyword: '', type: '', difficulty: '' }

export default function ExamQuestionsEditor({ exam, data, token, isAdmin, onClose, onSaved }) {
  const base = isAdmin ? '/admin' : '/teacher'
  const editable = data.status === 'DRAFT'
  const [selected, setSelected] = useState(() => data.questions.map(item => ({ ...item, points: item.points.toString() })))
  const [dirty, setDirty] = useState(false)
  const [draft, setDraft] = useState(emptyFilters)
  const [filters, setFilters] = useState(emptyFilters)
  const [page, setPage] = useState(1)
  const [loaded, setLoaded] = useState(null)
  const [failure, setFailure] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [preview, setPreview] = useState(null)
  const [retry, setRetry] = useState(0)
  const query = new URLSearchParams({ subjectId: exam.subjectId, active: true, page, size: 8, sortBy: 'id:desc', ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value)) }).toString()
  const key = `${base}?${query}&retry=${retry}`
  const bank = loaded?.key === key ? loaded.data : null
  const bankError = failure?.key === key ? failure.message : ''
  const totalCents = selected.reduce((total, item) => total + Math.round(Number(item.points || 0) * 100), 0)

  useEffect(() => {
    if (!editable) return
    const controller = new AbortController()
    api(`${base}/questions?${query}`, { token, signal: controller.signal })
      .then(value => setLoaded({ key, data: value }))
      .catch(error => { if (!controller.signal.aborted) setFailure({ key, message: error.message }) })
    return () => controller.abort()
  }, [base, editable, key, query, token])

  function change(items) { setSelected(items); setDirty(true); setPreview(null); setNotice(''); setError('') }
  function close() { if (!busy && (!dirty || window.confirm('Bạn có thay đổi chưa lưu. Đóng cửa sổ và bỏ các thay đổi này?'))) onClose() }
  function move(index, direction) {
    const items = [...selected]; const next = index + direction
    ;[items[index], items[next]] = [items[next], items[index]]
    change(items)
  }
  async function save() {
    setError(''); setNotice('')
    if (selected.some(item => !/^\d{1,6}(\.\d{1,2})?$/.test(item.points) || Number(item.points) < 0.01) || totalCents > 99999999) {
      setError('Điểm mỗi câu phải từ 0.01 đến 999999.99, tối đa 2 chữ số thập phân; tổng điểm tối đa 999999.99.'); return
    }
    setBusy(true)
    try {
      const value = await api(`${base}/exams/${exam.id}/questions`, { token, method: 'PUT', body: { questions: selected.map(item => ({ questionId: item.question.id, points: Number(item.points) })) } })
      setSelected(value.questions.map(item => ({ ...item, points: item.points.toString() }))); setDirty(false); setPreview(null)
      setNotice('Đã lưu câu hỏi, điểm và thứ tự trong đề.'); onSaved()
    } catch (error) { setError(error.message) }
    finally { setBusy(false) }
  }
  async function previewPaper() {
    setBusy(true); setError('')
    try { setPreview(await api(`${base}/exams/${exam.id}/questions/preview`, { token, method: 'POST' })) }
    catch (error) { setError(error.message) }
    finally { setBusy(false) }
  }

  return <div className="modal-backdrop"><div className="modal exam-questions-modal" role="dialog" aria-modal="true" aria-labelledby="exam-questions-title">
    <div className="modal-head"><div><span className="eyebrow">CÂU HỎI TRONG ĐỀ</span><h2 id="exam-questions-title">{exam.title}</h2></div><button aria-label="Đóng cửa sổ câu hỏi" disabled={busy} onClick={close}>×</button></div>
    <p>{exam.subjectCode} · {exam.subjectName} · {editable ? 'Bài nháp' : 'Cấu hình đã khóa'}</p>
    <p className="exam-form-note">Trộn câu hỏi: {exam.shuffleQuestions ? 'Bật' : 'Tắt'} · Trộn đáp án: {exam.shuffleOptions ? 'Bật' : 'Tắt'}. Có thể thay đổi trong cấu hình bài nháp.</p>
    {error && <div className="notice error" role="alert">{error}</div>}{notice && <div className="notice success" role="status">{notice}</div>}
    <div className={`exam-question-columns ${editable ? '' : 'read-only'}`}>
      {editable && <section className="exam-bank"><h3>Chọn từ ngân hàng câu hỏi</h3><p className="exam-form-note">Câu hỏi hoạt động cùng môn{isAdmin ? ' của tất cả người tạo' : ' do bạn tạo'}. Mỗi câu chỉ được gán một lần.</p>
        <form className="exam-bank-filters" onSubmit={event => { event.preventDefault(); setPage(1); setFilters({ ...draft, keyword: draft.keyword.trim() }) }}>
          <fieldset disabled={busy}><label className="field"><span>Tìm câu hỏi</span><input aria-label="Tìm câu hỏi" value={draft.keyword} onChange={event => setDraft({ ...draft, keyword: event.target.value })} placeholder="Nội dung câu hỏi…" /></label>
          <div className="two-cols"><label className="field"><span>Loại câu hỏi</span><select aria-label="Loại câu hỏi" value={draft.type} onChange={event => setDraft({ ...draft, type: event.target.value })}><option value="">Tất cả loại</option>{Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label className="field"><span>Độ khó</span><select aria-label="Độ khó" value={draft.difficulty} onChange={event => setDraft({ ...draft, difficulty: event.target.value })}><option value="">Tất cả độ khó</option>{Object.entries(levels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
          <button className="secondary">Tìm trong ngân hàng</button></fieldset>
        </form>
        {bankError ? <p role="alert">{bankError} <button onClick={() => setRetry(retry + 1)}>Thử lại</button></p> : !bank ? <p>Đang tải câu hỏi…</p> : !bank.questions.length ? <p>Không có câu hỏi phù hợp. Hãy thêm câu hỏi vào ngân hàng hoặc đổi bộ lọc.</p> : <ul className="exam-bank-list">{bank.questions.map(question => <li key={question.id}><div><span className="tag">#{question.id} · {types[question.type]} · {levels[question.difficulty]}</span><p>{question.content}</p><small>{question.createdByName}</small></div><button className="secondary" disabled={busy || selected.length >= 1000 || selected.some(item => item.question.id === question.id)} aria-label={`Thêm câu hỏi ${question.id}`} onClick={() => change([...selected, { question, points: '1' }])}>{selected.some(item => item.question.id === question.id) ? 'Đã chọn' : '+ Thêm'}</button></li>)}</ul>}
        {bank?.totalPages > 1 && <nav className="pagination" aria-label="Phân trang ngân hàng trong đề"><button disabled={page <= 1 || busy} onClick={() => setPage(page - 1)}>← Trước</button><span>Trang {page} / {bank.totalPages}</span><button disabled={page >= bank.totalPages || busy} onClick={() => setPage(page + 1)}>Sau →</button></nav>}
      </section>}
      <section className="exam-selected"><div className="exam-selection-heading"><h3>Câu hỏi đã chọn ({selected.length})</h3><strong>Tổng điểm: {Number.isFinite(totalCents) ? (totalCents / 100).toFixed(2) : '—'}</strong></div>
        {!selected.length ? <p className="exam-form-note">Đề chưa có câu hỏi. Chọn câu hỏi ở ngân hàng để bắt đầu.</p> : <ol className="exam-selected-list">{selected.map((item, index) => <li key={item.question.id}>
          <span className="tag">Câu {index + 1} · #{item.question.id} · {types[item.question.type]}</span>{!item.question.active && <span className="field-error">Câu hỏi đã ngừng hoạt động</span>}
          <p className="exam-question-content">{item.question.content}</p>
          {!!item.question.options.length && <details><summary>Xem đáp án nguồn</summary><ul className="exam-source-options">{item.question.options.map(option => <li key={option.id}>{option.content}{option.correct && <strong> · Đáp án đúng</strong>}</li>)}</ul></details>}
          <div className="exam-question-tools"><label className="field"><span>Điểm câu {index + 1}</span><input aria-label={`Điểm câu ${index + 1}`} type="number" min="0.01" max="999999.99" step="0.01" value={item.points} disabled={!editable || busy} onChange={event => change(selected.map((value, position) => position === index ? { ...value, points: event.target.value } : value))} /></label>
          {editable && <div className="actions"><button aria-label={`Đưa câu ${index + 1} lên`} disabled={index === 0 || busy} onClick={() => move(index, -1)}>↑</button><button aria-label={`Đưa câu ${index + 1} xuống`} disabled={index === selected.length - 1 || busy} onClick={() => move(index, 1)}>↓</button><button className="danger" aria-label={`Bỏ câu ${index + 1} khỏi đề`} disabled={busy} onClick={() => change(selected.filter((_, position) => position !== index))}>Bỏ</button></div>}</div>
        </li>)}</ol>}
      </section>
    </div>
    <div className="exam-paper-actions"><button className="secondary" disabled={busy || dirty || !selected.length} onClick={previewPaper}>{preview ? 'Trộn và xem thử lại' : 'Xem thử đề thi'}</button><span className="exam-form-note">{dirty ? 'Lưu thay đổi trước khi xem thử.' : 'Bản xem thử dùng cấu hình đã lưu; không tạo lượt thi.'}</span></div>
    {preview && <section className="exam-paper-preview"><h3>Bản xem thử · {preview.questions.length} câu · {preview.totalPoints} điểm</h3><p className="exam-form-note">Bản xem thử không đánh dấu đáp án đúng. Mỗi lượt thi sẽ lưu riêng thứ tự đã trộn.</p><ol>{preview.questions.map(question => <li key={question.examQuestionId}><strong>Câu {question.displayOrder} ({question.points} điểm)</strong><p className="exam-question-content">{question.content}</p>{question.type === 'ESSAY' ? <p className="exam-form-note">Phần trả lời tự luận</p> : <ol type="A">{question.options.map(option => <li key={option.displayOrder}>{option.content}</li>)}</ol>}</li>)}</ol></section>}
    <div className="modal-actions"><button className="secondary" disabled={busy} onClick={close}>Đóng</button>{editable && <button className="primary" disabled={busy || !dirty} onClick={save}>{busy ? 'Đang lưu…' : 'Lưu câu hỏi trong đề'}</button>}</div>
  </div></div>
}
