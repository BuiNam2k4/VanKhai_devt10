export function answerValue(type, value) {
  return type === 'ESSAY' ? (typeof value === 'string' && value.trim() ? value : '') : (value ?? null)
}

export function dirtyAnswers(entries) {
  return Object.values(entries).filter(entry => answerValue(entry.type, entry.value) !== answerValue(entry.type, entry.savedValue))
}

export function initialAnswers(questions, answers = []) {
  const saved = new Map(answers.map(answer => [answer.sessionQuestionId, answer]))
  return Object.fromEntries(questions.map(question => {
    const answer = saved.get(question.id)
    const value = answerValue(question.type, question.type === 'ESSAY' ? answer?.answerText : answer?.selectedOptionId)
    return [question.id, { id: question.id, type: question.type, value, savedValue: value, version: answer?.version ?? null, savedAt: answer?.savedAt ?? null, conflict: false }]
  }))
}

export function mergeServerAnswers(entries, answers, acknowledgement = false) {
  const next = { ...entries }
  for (const answer of answers) {
    const entry = next[answer.sessionQuestionId]
    if (!entry || (entry.version !== null && answer.version < entry.version)) continue
    const value = answerValue(entry.type, entry.type === 'ESSAY' ? answer.answerText : answer.selectedOptionId)
    const dirty = answerValue(entry.type, entry.value) !== answerValue(entry.type, entry.savedValue)
    if (acknowledgement || !dirty || answerValue(entry.type, entry.value) === value) {
      next[entry.id] = { ...entry, value: dirty ? entry.value : value, savedValue: value, version: answer.version, savedAt: answer.savedAt, conflict: false }
    } else if (answer.version !== entry.version) {
      next[entry.id] = { ...entry, conflict: true, serverValue: value, serverVersion: answer.version, serverSavedAt: answer.savedAt }
    }
  }
  return next
}

export function restoreDrafts(entries, drafts) {
  let next = { ...entries }
  for (const draft of Array.isArray(drafts) ? drafts : []) {
    const entry = next[draft.id]
    if (!entry || !(draft.version === null || (Number.isSafeInteger(draft.version) && draft.version >= 0))) continue
    if (entry.type === 'ESSAY' ? typeof draft.value !== 'string' || draft.value.length > 10000
      : draft.value !== null && (!Number.isSafeInteger(draft.value) || draft.value <= 0)) continue
    const value = answerValue(entry.type, draft.value)
    if (value === entry.savedValue) continue // The server accepted a previous save whose acknowledgement was lost.
    next[entry.id] = { ...entry, value: draft.value, version: draft.version,
      conflict: draft.version !== entry.version, serverValue: entry.savedValue, serverVersion: entry.version, serverSavedAt: entry.savedAt }
  }
  return next
}

export function resolveAnswerConflicts(entries, keepDraft) {
  return Object.fromEntries(Object.values(entries).map(entry => [entry.id, !entry.conflict ? entry : {
    ...entry, value: keepDraft ? entry.value : entry.serverValue, savedValue: entry.serverValue,
    version: entry.serverVersion, savedAt: entry.serverSavedAt, conflict: false,
  }]))
}

export function answerPayload(entries) {
  return dirtyAnswers(entries).map(entry => ({ sessionQuestionId: entry.id, version: entry.version,
    selectedOptionId: entry.type === 'SINGLE_CHOICE' ? entry.value : null,
    answerText: entry.type === 'ESSAY' ? answerValue(entry.type, entry.value) || null : null,
  }))
}

export function saveLocalAnswerDrafts(storage, key, entries) {
  const dirty = dirtyAnswers(entries)
  if (dirty.length) storage.setItem(key, JSON.stringify(dirty.map(({ id, value, version }) => ({ id, value, version }))))
  else storage.removeItem(key)
}
