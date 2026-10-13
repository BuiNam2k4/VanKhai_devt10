import test from 'node:test'
import assert from 'node:assert/strict'
import { answerPayload, dirtyAnswers, initialAnswers, mergeServerAnswers, resolveAnswerConflicts, restoreDrafts, saveLocalAnswerDrafts } from '../src/answer-state.js'

const questions = [{ id: 10, type: 'SINGLE_CHOICE' }, { id: 20, type: 'ESSAY' }]
const saved = (sessionQuestionId, value, version) => ({ sessionQuestionId, selectedOptionId: sessionQuestionId === 10 ? value : null,
  answerText: sessionQuestionId === 20 ? value : null, version, savedAt: '2030-01-01T00:00:00Z' })

test('an acknowledgement preserves text typed while the previous save was in flight', () => {
  const entries = initialAnswers(questions)
  entries[20].value = 'Newer local text'
  const merged = mergeServerAnswers(entries, [saved(20, 'Earlier sent text', 0)], true)
  assert.equal(merged[20].value, 'Newer local text')
  assert.equal(merged[20].savedValue, 'Earlier sent text')
  assert.equal(merged[20].version, 0)
  assert.deepEqual(answerPayload(merged), [{ sessionQuestionId: 20, version: 0, selectedOptionId: null, answerText: 'Newer local text' }])
})

test('an old server poll cannot roll back a newer acknowledged answer', () => {
  const entries = initialAnswers(questions, [saved(10, 101, 2)])
  const merged = mergeServerAnswers(entries, [saved(10, 100, 1)])
  assert.equal(merged[10].value, 101)
  assert.equal(merged[10].version, 2)
  assert.equal(dirtyAnswers(merged).length, 0)
})

test('server changes conflict with a dirty draft and require explicit resolution', () => {
  const entries = initialAnswers(questions, [saved(20, 'Original', 0)])
  entries[20].value = 'My draft'
  const conflicted = mergeServerAnswers(entries, [saved(20, 'Other tab', 1)])
  assert.equal(conflicted[20].value, 'My draft')
  assert.equal(conflicted[20].conflict, true)
  const keep = resolveAnswerConflicts(conflicted, true)
  assert.equal(keep[20].value, 'My draft')
  assert.equal(keep[20].version, 1)
  assert.equal(keep[20].conflict, false)
  assert.equal(dirtyAnswers(keep).length, 1)
  const accept = resolveAnswerConflicts(conflicted, false)
  assert.equal(accept[20].value, 'Other tab')
  assert.equal(dirtyAnswers(accept).length, 0)
})

test('reloading restores a pending draft only for questions in this paper', () => {
  const entries = initialAnswers(questions)
  const restored = restoreDrafts(entries, [{ id: 20, value: 'Tiếng Việt\nDòng hai', version: null }, { id: 99, value: 'foreign', version: null }])
  assert.equal(restored[20].value, 'Tiếng Việt\nDòng hai')
  assert.equal(restored[99], undefined)
  assert.equal(dirtyAnswers(restored).length, 1)
})

test('a lost acknowledgement is recognized after reloading without resending stale data', () => {
  const restored = restoreDrafts(initialAnswers(questions, [saved(20, 'Accepted', 0)]), [{ id: 20, value: 'Accepted', version: null }])
  assert.equal(restored[20].version, 0)
  assert.equal(dirtyAnswers(restored).length, 0)
})

test('a stale local draft never overwrites a more recent server answer automatically', () => {
  const restored = restoreDrafts(initialAnswers(questions, [saved(10, 102, 2)]), [{ id: 10, value: 101, version: 0 }])
  assert.equal(restored[10].value, 101)
  assert.equal(restored[10].conflict, true)
  assert.equal(resolveAnswerConflicts(restored, false)[10].value, 102)
})

test('clearing a choice or essay sends explicit nulls with its current server version', () => {
  const entries = initialAnswers(questions, [saved(10, 101, 0), saved(20, 'Text', 3)])
  entries[10].value = null
  entries[20].value = '   '
  assert.deepEqual(answerPayload(entries), [
    { sessionQuestionId: 10, version: 0, selectedOptionId: null, answerText: null },
    { sessionQuestionId: 20, version: 3, selectedOptionId: null, answerText: null },
  ])
})

test('malformed local drafts do not replace valid server state', () => {
  const entries = initialAnswers(questions, [saved(20, 'Server', 0)])
  const restored = restoreDrafts(entries, [{ id: 20, value: {}, version: 0 }, { id: 10, value: 101, version: -1 }])
  assert.equal(restored[20].value, 'Server')
  assert.equal(restored[10].value, null)
})

test('one tab finishing its save cannot remove the pending local draft of another tab', () => {
  const values = new Map()
  const storage = { setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
  const first = initialAnswers(questions)
  first[20].value = 'Unsent draft in tab A'
  saveLocalAnswerDrafts(storage, 'session-1-tab-A', first)
  saveLocalAnswerDrafts(storage, 'session-1-tab-B', initialAnswers(questions, [saved(20, 'Saved by tab B', 0)]))
  const restored = restoreDrafts(initialAnswers(questions, [saved(20, 'Saved by tab B', 0)]), JSON.parse(values.get('session-1-tab-A')))
  assert.equal(restored[20].value, 'Unsent draft in tab A')
  assert.equal(restored[20].conflict, true)
})
