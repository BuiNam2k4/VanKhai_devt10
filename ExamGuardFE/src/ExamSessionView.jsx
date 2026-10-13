import { useEffect, useState } from 'react'
import { api } from './api'
import { countdown, getDeviceId, remainingSeconds } from './exam-session'
import useAnswerAutosave from './useAnswerAutosave'
import { answerValue, dirtyAnswers } from './answer-state'
import './exam-session.css'

export default function ExamSessionView({ sessionId, token, onBack }) {
  const [snapshot, setSnapshot] = useState(null)
  const [failure, setFailure] = useState(null)
  const [monotonicNow, setMonotonicNow] = useState(() => performance.now())
  const [retry, setRetry] = useState(0)
  const key = `${sessionId}:${token}`
  const saved = snapshot?.key === key ? snapshot : null
  const error = failure?.key === key ? failure : null
  const sessionBlocked = error && [401, 403, 404, 409].includes(error.status)
  const session = saved?.data
  const seconds = session?.canContinue ? remainingSeconds(saved.anchor, monotonicNow) : 0
  const expired = session && (session.expired || (session.canContinue && seconds === 0))
  const answers = useAnswerAutosave({ sessionId, token, session, enabled: !!session?.canContinue && !expired && !sessionBlocked })
  const blocked = sessionBlocked || answers.fatal
  const readOnly = !answers.ready || !session?.canContinue || expired || blocked || answers.ended
  const dirtyIds = new Set(dirtyAnswers(answers.entries).map(entry => entry.id))
  const answeredCount = Object.values(answers.entries).filter(entry => answerValue(entry.type, entry.value) !== (entry.type === 'ESSAY' ? '' : null)).length

  async function back() {
    if (!readOnly && answers.dirtyCount && !await answers.saveNow()) return
    onBack()
  }

  useEffect(() => {
    const controller = new AbortController()
    let syncing = false; let latest = null; let expiryRequested = false
    async function sync() {
      if (syncing || controller.signal.aborted) return
      syncing = true
      const started = performance.now()
      try {
        const data = await api(`/student/sessions/${sessionId}?deviceId=${encodeURIComponent(getDeviceId())}`, { token, signal: controller.signal })
        if (controller.signal.aborted) return
        const received = performance.now()
        const anchor = { expiresAt: Date.parse(data.expiresAt), serverTime: Date.parse(data.serverTime) + (received - started) / 2, receivedAt: received }
        latest = { key, data, anchor }; expiryRequested = false
        setSnapshot(latest); setFailure(null)
      } catch (error) { if (!controller.signal.aborted) setFailure({ key, message: error.message, status: error.status }) }
      finally { syncing = false }
    }
    const tick = window.setInterval(() => {
      const now = performance.now(); setMonotonicNow(now)
      if (latest?.data.canContinue && remainingSeconds(latest.anchor, now) === 0 && !expiryRequested) { expiryRequested = true; sync() }
    }, 1000)
    const poll = window.setInterval(() => { if (!document.hidden) sync() }, 30000)
    const visible = () => { if (!document.hidden) sync() }
    window.addEventListener('focus', sync); window.addEventListener('online', sync); document.addEventListener('visibilitychange', visible)
    sync()
    return () => { controller.abort(); window.clearInterval(tick); window.clearInterval(poll); window.removeEventListener('focus', sync); window.removeEventListener('online', sync); document.removeEventListener('visibilitychange', visible) }
  }, [key, retry, sessionId, token])

  return <main className="exam-session-workspace"><button className="back-button" onClick={back}>← Danh sách bài thi</button>
    {error && <div className="notice error" role="alert">{error.message}<button className="secondary" onClick={() => setRetry(retry + 1)}>Thử lại</button></div>}
    {answers.error && <div className="notice error" role="alert">{answers.error}</div>}
    {blocked ? <div className="table-card table-state">Không thể mở phiên thi này. Hãy kiểm tra tài khoản, lớp được giao và thiết bị bắt đầu lượt thi.</div> : !session ? <div className="table-card table-state">Đang tải đề và thời hạn phiên thi…</div> : <>
      <div className="exam-session-heading"><div><span className="eyebrow">PHIÊN THI #{session.id} · LƯỢT {session.attemptNumber}</span><h1>{session.examTitle}</h1><p>{session.subjectName} · {session.questions.length} câu · Tổng {session.maxScore} điểm</p></div>
        <div className={`exam-countdown ${expired ? 'expired' : seconds < 300 ? 'near-end' : ''}`}><span>Thời gian còn lại</span><strong role="timer" aria-label="Thời gian còn lại" aria-live="off">{countdown(seconds)}</strong></div></div>
      {expired ? <div className="notice error" role="status">Hết thời gian làm bài. Thời hạn của lượt này đã kết thúc.</div> : !session.canContinue ? <div className="notice success" role="status">Lượt thi đã kết thúc.</div> : <p className="session-time-note">Đề và thời hạn của lượt thi được giữ nguyên khi tải lại trang. Đóng cửa sổ hoặc quay về danh sách không dừng bộ đếm.</p>}
      <dl className="exam-session-times"><div><dt>Bắt đầu</dt><dd>{time(session.startedAt)}</dd></div><div><dt>Hết hạn</dt><dd>{time(session.expiresAt)}</dd></div><div><dt>Giờ máy chủ lúc đồng bộ</dt><dd>{time(session.serverTime)}</dd></div></dl>
      <div className="answer-save-bar"><div><strong>{answeredCount} / {session.questions.length} câu đã trả lời</strong><p role="status" aria-live="polite">{answers.saving ? 'Đang lưu đáp án…' : answers.dirtyCount ? `${answers.dirtyCount} câu chưa lưu lên máy chủ` : answeredCount ? 'Đã lưu tất cả đáp án lên máy chủ' : 'Chưa có đáp án'}</p><small>Tự lưu sau khi ngừng nhập và kiểm tra mỗi 10 giây.</small></div><button className="primary" disabled={readOnly || answers.saving || !answers.dirtyCount || answers.conflict} onClick={() => answers.saveNow()}>Lưu ngay</button></div>
      {answers.storageError && <div className="notice error" role="alert">{answers.storageError}</div>}
      {(expired || answers.ended) && answers.dirtyCount > 0 && <div className="notice error" role="alert">Có đáp án chưa lưu lên máy chủ trước hạn. Bản nháp tại trình duyệt không được tính là đáp án đã lưu.</div>}
      {answers.conflict && <div className="answer-conflict" role="alert"><p>Một số đáp án đã thay đổi từ tab khác. Phần đang nhập được giữ lại; hãy chọn bản muốn dùng trước khi lưu tiếp.</p><div><button className="secondary" onClick={() => answers.resolve(false)}>Dùng đáp án máy chủ</button><button className="primary" disabled={readOnly || answers.saving} onClick={() => answers.resolve(true)}>Lưu bản đang nhập</button></div></div>}
      <section className="session-paper" aria-label="Đề của phiên thi">{session.questions.map(question => <article className="session-question" key={question.id}>
        <div className="session-question-heading"><h2>Câu {question.displayOrder}</h2><span className="tag">{question.points} điểm · {question.type === 'ESSAY' ? 'Tự luận' : 'Trắc nghiệm'}</span></div><p className="session-question-content">{question.content}</p>
        <fieldset className="session-answer" disabled={readOnly}><legend>{question.type === 'ESSAY' ? 'Nhập câu trả lời' : 'Chọn một đáp án'}</legend>
          {question.type === 'SINGLE_CHOICE' ? <><div className="session-choices">{question.options.map((option, index) => <label className={`session-choice ${answers.entries[question.id]?.value === option.id ? 'selected' : ''}`} key={option.id}><input type="radio" name={`answer-${sessionId}-${question.id}`} aria-label={`Câu ${question.displayOrder}, đáp án ${String.fromCharCode(65 + index)}`} checked={answers.entries[question.id]?.value === option.id} onChange={() => answers.edit(question, option.id)} /><span><b>{String.fromCharCode(65 + index)}.</b> {option.content}</span></label>)}</div><button className="text-button" type="button" disabled={answers.entries[question.id]?.value == null} onClick={() => answers.edit(question, null)}>Bỏ chọn</button></> : <><textarea aria-label={`Đáp án câu ${question.displayOrder}`} value={answers.entries[question.id]?.value || ''} onChange={event => answers.edit(question, event.target.value)} rows={6} maxLength={10000} placeholder="Nhập câu trả lời của bạn…" /><small>{(answers.entries[question.id]?.value || '').length} / 10000 ký tự</small></>}
        </fieldset>
        <p className={`question-save-state ${dirtyIds.has(question.id) || answers.entries[question.id]?.conflict ? 'pending' : ''}`}>{answers.entries[question.id]?.conflict ? 'Có thay đổi từ tab khác' : dirtyIds.has(question.id) ? 'Chưa lưu lên máy chủ' : answers.entries[question.id]?.savedAt ? `Đã lưu: ${time(answers.entries[question.id].savedAt)}` : 'Chưa có đáp án đã lưu'}</p>
      </article>)}</section>
    </>}
  </main>
}
function time(value) { return new Date(value).toLocaleString('vi-VN') }
