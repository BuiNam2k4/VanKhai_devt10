import { useEffect, useRef, useState } from 'react'
import { api } from './api'
import { getDeviceId } from './exam-session'
import { answerPayload, dirtyAnswers, initialAnswers, mergeServerAnswers, resolveAnswerConflicts, restoreDrafts, saveLocalAnswerDrafts } from './answer-state'

function answerTabId() {
  const id = sessionStorage.getItem('examguard.answerTabId') || crypto.randomUUID()
  sessionStorage.setItem('examguard.answerTabId', id)
  return id
}

export default function useAnswerAutosave({ sessionId, token, session, enabled }) {
  const runtime = useRef(null)
  const [view, setView] = useState(null)
  const key = `${sessionId}:${token}`

  function publish(rt) {
    if (runtime.current !== rt || rt.controller.signal.aborted) return
    const dirty = dirtyAnswers(rt.entries)
    try {
      if (!rt.storageKey) throw new Error('Draft storage unavailable')
      saveLocalAnswerDrafts(localStorage, rt.storageKey, rt.entries)
      rt.storageError = ''
    } catch { rt.storageError = 'Trình duyệt không giữ được bản nháp tại máy. Hãy dùng Lưu ngay trước khi rời trang.' }
    setView({ key: rt.key, entries: rt.entries, dirtyCount: dirty.length, saving: !!rt.pending,
      error: rt.error, storageError: rt.storageError, conflict: Object.values(rt.entries).some(entry => entry.conflict), ended: rt.ended, fatal: rt.fatal })
  }

  async function flush(rt) {
    if (!rt || rt.controller.signal.aborted) return false
    if (rt.pending) { const success = await rt.pending; return success && (dirtyAnswers(rt.entries).length ? flush(rt) : !rt.error) }
    if (!dirtyAnswers(rt.entries).length) return true
    if (!rt.enabled || rt.ended || rt.fatal || Object.values(rt.entries).some(entry => entry.conflict)) return false
    if (!navigator.onLine) { rt.error = 'Mất kết nối. Đáp án chưa lưu lên máy chủ; sẽ thử lại khi có mạng.'; publish(rt); return false }
    const body = { deviceId: rt.deviceId, answers: answerPayload(rt.entries) }
    rt.error = ''
    rt.pending = (async () => {
      try {
        const data = await api(`/student/sessions/${sessionId}/answers`, { token, method: 'PUT', body, signal: rt.controller.signal })
        if (rt.controller.signal.aborted) return false
        // Preserve edits made while this request was in flight; only advance their server baseline.
        rt.entries = mergeServerAnswers(rt.entries, data.answers, true)
        rt.ended = !data.canSave
        return true
      } catch (error) {
        if (rt.controller.signal.aborted) return false
        rt.error = error.status ? error.message : 'Không kết nối được máy chủ. Đáp án chưa lưu; sẽ tự thử lại và vẫn giữ bản đang nhập.'
        rt.fatal = [401, 403, 404].includes(error.status)
        if (error.status === 409) {
          try {
            const data = await api(`/student/sessions/${sessionId}/answers?deviceId=${encodeURIComponent(rt.deviceId)}`, { token, signal: rt.controller.signal })
            if (!rt.controller.signal.aborted) { rt.entries = mergeServerAnswers(rt.entries, data.answers); rt.ended = !data.canSave }
          } catch (refreshError) { rt.fatal = [401, 403, 404, 409].includes(refreshError.status) }
        }
        return false
      } finally {
        rt.pending = null
        publish(rt)
      }
    })()
    publish(rt)
    return rt.pending
  }

  useEffect(() => {
    const deviceId = getDeviceId()
    let storageKey = null
    try { storageKey = `examguard.answerDrafts.${sessionId}.${deviceId}.${answerTabId()}` } catch { /* Saving to the server remains available when browser draft storage is blocked. */ }
    const rt = { key, entries: {}, initialized: false, enabled: false, pending: null, error: '', storageError: '', ended: false, fatal: false,
      controller: new AbortController(), deviceId, storageKey, debounce: null }
    runtime.current = rt
    const periodic = window.setInterval(() => { flush(rt) }, 10000)
    const online = () => { flush(rt) }
    const offline = () => { if (dirtyAnswers(rt.entries).length) { rt.error = 'Mất kết nối. Đáp án chưa lưu lên máy chủ; sẽ thử lại khi có mạng.'; publish(rt) } }
    const beforeUnload = event => { if (dirtyAnswers(rt.entries).length) { event.preventDefault(); event.returnValue = '' } }
    window.addEventListener('online', online); window.addEventListener('offline', offline); window.addEventListener('beforeunload', beforeUnload)
    return () => { rt.controller.abort(); window.clearInterval(periodic); window.clearTimeout(rt.debounce); window.removeEventListener('online', online); window.removeEventListener('offline', offline); window.removeEventListener('beforeunload', beforeUnload) }
    // A runtime is scoped to one authenticated attempt. Polling uses its current mutable draft.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [key, sessionId, token])

  useEffect(() => {
    const rt = runtime.current
    if (!session || !rt || rt.key !== key) return
    rt.enabled = enabled
    if (!rt.initialized) {
      rt.entries = initialAnswers(session.questions, session.answers)
      try { if (rt.storageKey) rt.entries = restoreDrafts(rt.entries, JSON.parse(localStorage.getItem(rt.storageKey) || '[]')) } catch { /* A missing or invalid local draft never replaces server answers. */ }
      rt.initialized = true
    } else if (!rt.pending) {
      rt.entries = mergeServerAnswers(rt.entries, session.answers || [])
      if (!dirtyAnswers(rt.entries).length && !rt.fatal) rt.error = ''
    }
    publish(rt)
    // Only server snapshots and write eligibility update the draft baseline.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, key, session])

  function edit(question, value) {
    const rt = runtime.current
    if (!rt?.enabled || rt.ended || rt.fatal || !rt.entries[question.id]) return
    rt.entries = { ...rt.entries, [question.id]: { ...rt.entries[question.id], value } }
    publish(rt)
    window.clearTimeout(rt.debounce)
    rt.debounce = window.setTimeout(() => { flush(rt) }, 1500)
  }
  function resolve(keepDraft) {
    const rt = runtime.current
    if (!rt) return
    rt.entries = resolveAnswerConflicts(rt.entries, keepDraft); rt.error = ''; publish(rt)
    if (keepDraft) flush(rt)
  }
  return { ...(view?.key === key ? view : { entries: {}, dirtyCount: 0, saving: false, conflict: false }),
    ready: !!runtime.current?.initialized && runtime.current.key === key,
    edit, saveNow: () => flush(runtime.current), resolve,
  }
}
