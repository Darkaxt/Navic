import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createPaginatorCommitReceiptTraceSink,
  projectPaginatorCommitReceiptTrace,
  validatePaginatorCommitReceiptSummary,
} from './paginator-commit-receipt-acceptance.mjs'

test('projects only the numeric exact-settlement page index', () => {
  assert.deepEqual(
    projectPaginatorCommitReceiptTrace({
      type: 'page-turn:exact-settled',
      payload: {
        pageIndex: 12,
        href: 'protected',
        cfi: 'protected',
        title: 'protected',
        text: 'protected',
        url: 'protected',
        bookId: 'protected',
      },
    }),
    { state: 'accepted', pageIndex: 12 },
  )
  assert.equal(
    JSON.stringify(projectPaginatorCommitReceiptTrace({
      type: 'page-turn:exact-settled',
      payload: { pageIndex: 12, href: 'protected' },
    })).includes('protected'),
    false,
  )
})

test('ignores unrelated traces and rejects malformed settlements', () => {
  assert.equal(
    projectPaginatorCommitReceiptTrace({
      type: 'location:post',
      payload: { pageIndex: 12 },
    }),
    null,
  )
  assert.deepEqual(
    projectPaginatorCommitReceiptTrace({
      type: 'page-turn:exact-settled',
      payload: { pageIndex: 'not-a-page' },
    }),
    { state: 'malformed', pageIndex: null },
  )
})

test('bounds projected receipts and records overflow', () => {
  const sink = createPaginatorCommitReceiptTraceSink(2)
  for (const pageIndex of [1, 2, 3]) {
    sink.push({ type: 'page-turn:exact-settled', payload: { pageIndex } })
  }
  assert.deepEqual(sink.entries, [
    { state: 'accepted', pageIndex: 2 },
    { state: 'accepted', pageIndex: 3 },
  ])
  assert.equal(sink.droppedReceiptCount, 1)
  assert.equal(sink.malformedReceiptCount, 0)
})

test('validates exactly twenty accepted settlements', () => {
  const summary = {
    state: 'passed',
    acceptedForwardSettlements: 20,
    terminalState: 'none',
    droppedReceiptCount: 0,
    malformedReceiptCount: 0,
    chapterTransitions: [],
  }
  assert.equal(validatePaginatorCommitReceiptSummary(summary), true)
  assert.throws(
    () => validatePaginatorCommitReceiptSummary({
      ...summary,
      acceptedForwardSettlements: 19,
    }),
    /count mismatch/,
  )
  assert.throws(
    () => validatePaginatorCommitReceiptSummary(summary, {
      requireChapterTransition: true,
    }),
    /no chapter transition/,
  )
  assert.equal(
    validatePaginatorCommitReceiptSummary({
      ...summary,
      chapterTransitions: [{ settlement: 7, fromChapterIndex: 1, toChapterIndex: 2 }],
    }, { requireChapterTransition: true }),
    true,
  )
})
