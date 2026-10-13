import { useEffect, useState } from 'react'
import { api } from './api'
import { getDeviceId } from './exam-session'
import './student-exams.css'

const labels = { AVAILABLE: 'Có thể tham gia', UPCOMING: 'Chưa mở', IN_PROGRESS: 'Có lượt đang làm', SESSION_EXPIRED: 'Lượt đã hết giờ', ATTEMPTS_EXHAUSTED: 'Hết lượt thi', EXPIRED: 'Hết lịch thi', CLOSED: 'Đã đóng', UNAVAILABLE: 'Chưa thể tham gia' }
const policies = { AFTER_SUBMISSION: 'Sau khi nộp bài', AFTER_EXAM_END: 'Sau khi kết thúc lịch thi', HIDDEN: 'Không công bố cho sinh viên' }
const initial = { keyword: '', subjectId: '', status: '', window: '', sortBy: 'startsAt:desc' }

export default function StudentExams({ token, onSession }) {
  const [filters, setFilters] = useState(initial)
  const [draft, setDraft] = useState(initial)
  const [page, setPage] = useState(1)
  const [version, setVersion] = useState(0)
  const [loaded, setLoaded] = useState(null)
  const [failure, setFailure] = useState(null)
  const [subjects, setSubjects] = useState([])
  const [catalogError, setCatalogError] = useState('')
  const [detail, setDetail] = useState(null)
  const [detailError, setDetailError] = useState('')
  const [opening, setOpening] = useState(null)
  const [starting, setStarting] = useState(null)
  const query = new URLSearchParams({ page, size: 6, ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '')) }).toString()
  const key = `${query}&refresh=${version}`
  const result = loaded?.key === key ? loaded.data : null
  const error = failure?.key === key ? failure.message : ''

  useEffect(() => {
    const controller = new AbortController()
    api(`/student/exams?${query}`, { token, signal: controller.signal }).then(data => setLoaded({ key, data }))
      .catch(error => { if (!controller.signal.aborted) setFailure({ key, message: error.message }) })
    return () => controller.abort()
  }, [key, query, token])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      const values = []; let current = 1; let total = 1
      do {
        const data = await api(`/student/subjects?page=${current}&size=100`, { token, signal: controller.signal })
        values.push(...data.subjects); total = data.totalPages; current++
      } while (current <= total)
      if (!controller.signal.aborted) { setSubjects(values); setCatalogError('') }
    }
    load().catch(error => { if (!controller.signal.aborted) setCatalogError(error.message) })
    return () => controller.abort()
  }, [token, version])

  useEffect(() => {
    const refresh = () => { if (!document.hidden) setVersion(value => value + 1) }
    const timer = window.setInterval(refresh, 60000)
    window.addEventListener('focus', refresh)
    return () => { window.clearInterval(timer); window.removeEventListener('focus', refresh) }
  }, [])

  async function open(item) {
    setOpening(item.id); setDetailError('')
    try { setDetail(await api(`/student/exams/${item.id}`, { token })) }
    catch (error) { setDetailError(error.message) }
    finally { setOpening(null) }
  }
  async function start(item) {
    setStarting(item.id); setDetailError('')
    try {
      const session = await api(`/student/exams/${item.id}/sessions`, { token, method: 'POST', body: { deviceId: getDeviceId() } })
      onSession(session.id)
    } catch (error) { setDetailError(error.message); setVersion(value => value + 1) }
    finally { setStarting(null) }
  }

  return <section className="student-exams"><div className="page-heading"><div><span className="eyebrow">LỊCH THI VÀ ĐIỀU KIỆN THAM GIA</span><h1>Bài thi của tôi</h1><p>{result ? `${result.totalElements} bài thi được giao` : 'Danh sách bài thi thuộc các lớp của bạn'}</p></div><button className="secondary" onClick={() => setVersion(value => value + 1)}>Làm mới</button></div>
      {detailError && !detail && <div className="notice error" role="alert">{detailError}</div>}
      {catalogError && <div className="notice error" role="alert">Không tải được danh mục môn: {catalogError}</div>}
      <form className="student-exam-filters" onSubmit={event => { event.preventDefault(); setPage(1); setFilters({ ...draft, keyword: draft.keyword.trim() }) }}>
        <label className="field"><span>Tìm bài thi</span><input aria-label="Tìm bài thi" value={draft.keyword} onChange={event => setDraft({ ...draft, keyword: event.target.value })} placeholder="Tên bài thi, môn học hoặc mô tả…" /></label>
        <label className="field"><span>Môn học</span><select aria-label="Môn học" value={draft.subjectId} onChange={event => setDraft({ ...draft, subjectId: event.target.value })}><option value="">Tất cả môn được giao</option>{subjects.map(subject => <option key={subject.id} value={subject.id}>{subject.code} · {subject.name}</option>)}</select></label>
        <label className="field"><span>Trạng thái đề</span><select aria-label="Trạng thái đề" value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value })}><option value="">Tất cả trạng thái</option><option value="PUBLISHED">Đã công bố</option><option value="CLOSED">Đã đóng</option></select></label>
        <label className="field"><span>Lịch thi</span><select aria-label="Lịch thi" value={draft.window} onChange={event => setDraft({ ...draft, window: event.target.value })}><option value="">Tất cả lịch</option><option value="UPCOMING">Chưa đến giờ mở</option><option value="OPEN">Trong thời gian mở</option><option value="ENDED">Đã hết thời gian</option></select></label>
        <label className="field"><span>Sắp xếp</span><select aria-label="Sắp xếp" value={draft.sortBy} onChange={event => setDraft({ ...draft, sortBy: event.target.value })}><option value="startsAt:desc">Thời gian mở mới nhất</option><option value="startsAt:asc">Thời gian mở tăng dần</option><option value="title:asc">Tiêu đề A–Z</option><option value="endsAt:asc">Thời gian đóng tăng dần</option></select></label>
        <div className="student-filter-actions"><button className="primary">Áp dụng</button><button type="button" className="secondary" onClick={() => { setDraft(initial); setFilters(initial); setPage(1) }}>Xóa bộ lọc</button></div>
      </form>
      <p className="student-sync-note">{result ? `Cập nhật theo giờ máy chủ: ${time(result.serverTime)}. ` : ''}Trạng thái được kiểm tra lại khi xem chi tiết và làm mới mỗi phút.</p>
      <section aria-busy={!result && !error}>{error ? <div className="table-card table-state" role="alert">{error}<button className="secondary" onClick={() => setVersion(value => value + 1)}>Thử lại</button></div> : !result ? <div className="table-card table-state">Đang tải bài thi…</div> : !result.exams.length ? <div className="table-card table-state">Chưa có bài thi phù hợp. Đề nháp chưa được hiển thị; hãy kiểm tra bộ lọc hoặc liên hệ giảng viên về lớp được giao.</div> : <div className="student-exam-grid">{result.exams.map(item => <article className="student-exam-card" key={item.id}>
        <div className="student-card-tags"><span className={`tag participation-${item.participationStatus.toLowerCase()}`}>{labels[item.participationStatus]}</span><span className="tag">{item.status === 'CLOSED' ? 'Đề đã đóng' : 'Đề đã công bố'}</span></div>
        <h2>{item.title}</h2><p className="student-subject">{item.subjectCode} · {item.subjectName}</p><p className="student-teacher">Giảng viên: {item.teacherName}</p>
        <dl className="student-exam-facts"><div><dt>Mở</dt><dd>{time(item.startsAt)}</dd></div><div><dt>Đóng</dt><dd>{time(item.endsAt)}</dd></div><div><dt>Thời lượng</dt><dd>{item.durationMinutes} phút / lượt</dd></div><div><dt>Câu hỏi</dt><dd>{item.questionCount} câu</dd></div><div><dt>Lượt đã dùng</dt><dd>{item.attemptsUsed} / {item.maxAttempts}</dd></div><div><dt>Lượt còn lại</dt><dd>{item.attemptsRemaining}</dd></div></dl>
        <p className="student-participation-message">{item.participationMessage}</p><div className="student-session-actions"><button className="secondary" disabled={opening !== null || starting !== null} onClick={() => open(item)}>{opening === item.id ? 'Đang kiểm tra…' : 'Xem bài thi'}</button>{(item.canStart || item.canResume || item.participationStatus === 'SESSION_EXPIRED') && <button className="primary" disabled={starting !== null || opening !== null} onClick={() => start(item)}>{starting === item.id ? 'Đang mở phiên…' : item.canStart ? 'Bắt đầu thi' : item.canResume ? 'Tiếp tục thi' : 'Xem phiên hết giờ'}</button>}</div>
      </article>)}</div>}</section>
      {result?.totalPages > 1 && <nav className="pagination" aria-label="Phân trang bài thi sinh viên"><button disabled={page <= 1} onClick={() => setPage(page - 1)}>← Trước</button><span>Trang {page} / {result.totalPages}</span><button disabled={page >= result.totalPages} onClick={() => setPage(page + 1)}>Sau →</button></nav>}
    {detail && <div className="modal-backdrop"><div className="modal student-exam-detail" role="dialog" aria-modal="true" aria-labelledby="student-exam-title"><div className="modal-head"><div><span className="eyebrow">THÔNG TIN BÀI THI</span><h2 id="student-exam-title">{detail.title}</h2></div><button aria-label="Đóng chi tiết bài thi" onClick={() => setDetail(null)}>×</button></div>
      {detailError && <div className="notice error" role="alert">{detailError}</div>}<span className={`tag participation-${detail.participationStatus.toLowerCase()}`}>{labels[detail.participationStatus]}</span><p className="student-participation-message">{detail.participationMessage}</p>
      <dl className="student-exam-facts"><div><dt>Môn học</dt><dd>{detail.subjectCode} · {detail.subjectName}</dd></div><div><dt>Giảng viên</dt><dd>{detail.teacherName}</dd></div><div><dt>Mở</dt><dd>{time(detail.startsAt)}</dd></div><div><dt>Đóng</dt><dd>{time(detail.endsAt)}</dd></div><div><dt>Thời lượng</dt><dd>{detail.durationMinutes} phút</dd></div><div><dt>Số câu hỏi</dt><dd>{detail.questionCount}</dd></div><div><dt>Lượt đã dùng / tối đa</dt><dd>{detail.attemptsUsed} / {detail.maxAttempts}</dd></div><div><dt>Lượt còn lại</dt><dd>{detail.attemptsRemaining}</dd></div><div><dt>Hiển thị kết quả</dt><dd>{policies[detail.resultVisibility]}</dd></div>{detail.activeSessionId && <div><dt>Hạn lượt hiện tại</dt><dd>{time(detail.activeSessionExpiresAt)}</dd></div>}</dl>
      {detail.description && !['EXAMGUARD_DEMO_V1', 'EXAMGUARD_PRACTICE_V1'].includes(detail.description) && <p className="student-exam-description">{detail.description}</p>}<p className="student-sync-note">Kiểm tra theo giờ máy chủ: {time(detail.serverTime)}</p><div className="modal-actions"><button className="secondary" disabled={starting !== null} onClick={() => setDetail(null)}>Đóng</button>{(detail.canStart || detail.canResume || detail.participationStatus === 'SESSION_EXPIRED') && <button className="primary" disabled={starting !== null} onClick={() => start(detail)}>{starting !== null ? 'Đang mở phiên…' : detail.canStart ? 'Bắt đầu thi' : detail.canResume ? 'Tiếp tục thi' : 'Xem phiên hết giờ'}</button>}</div>
    </div></div>}
  </section>
}
function time(value) { return value ? new Date(value).toLocaleString('vi-VN') : 'Chưa đặt lịch' }
