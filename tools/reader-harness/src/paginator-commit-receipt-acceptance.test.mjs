import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createPaginatorCommitReceiptTraceSink,
  projectPaginatorCommitReceiptTrace,
  projectPaginatorNativeLogLine,
  shouldRetryPaginatorWarmup,
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

test('projects only whitelisted numeric native turn state', () => {
  assert.deepEqual(
    projectPaginatorNativeLogLine(
      '  131556.873 I Reader native tap action=RIGHT protected-publication-data',
    ),
    { timestamp: 131556.873, type: 'tap', action: 'RIGHT' },
  )
  assert.deepEqual(
    projectPaginatorNativeLogLine(
      '131556.900 I Reader gesture terminal gestureId=41 outcome=CommittedForward won=true detail=protected',
    ),
    {
      timestamp: 131556.9,
      type: 'terminal',
      outcome: 'CommittedForward',
      won: true,
    },
  )
  assert.deepEqual(
    projectPaginatorNativeLogLine(
      '131560.201 I Readiness transition ignored-fields interaction=BackgroundPrefetch->Ready reason=protected',
    ),
    { timestamp: 131560.201, type: 'readiness', state: 'Ready' },
  )
  assert.deepEqual(
    projectPaginatorNativeLogLine(
      '131564.000 I reader-relocation session=protected token=protected source=2 target=3 logicalDirection=Next state=Completed rejectionReason=None protected',
    ),
    {
      timestamp: 131564,
      type: 'relocation',
      pageIndex: 3,
      state: 'Completed',
      rejectionReason: 'None',
    },
  )
  assert.deepEqual(
    projectPaginatorNativeLogLine(
      '131559.468 E PlayLikeCurl visual handoff content validation failed reason=ContentRejected protected',
    ),
    {
      timestamp: 131559.468,
      type: 'handoff-failure',
      reason: 'ContentRejected',
    },
  )
})

test('drops unrelated and unrecognized native log payloads', () => {
  assert.equal(
    projectPaginatorNativeLogLine('131500.000 I protected publication title and location'),
    null,
  )
  assert.equal(
    projectPaginatorNativeLogLine(
      '131500.000 I Reader native tap action=PROTECTED protected-publication-data',
    ),
    null,
  )
  assert.equal(
    projectPaginatorNativeLogLine(
      '131500.000 I Readiness transition interaction=Ready->Protected reason=protected',
    ),
    null,
  )
})

test('retries only a ready warm-up interaction with no committed relocation', () => {
  const warmup = {
    sawNativeTap: true,
    readiness: 'Ready',
    retryWhenReady: false,
    sawNativeReadyAfterBusy: true,
    committedNativeTurn: false,
    sawNativeRelocation: false,
  }
  assert.equal(shouldRetryPaginatorWarmup(warmup), true)
  assert.equal(
    shouldRetryPaginatorWarmup({ ...warmup, committedNativeTurn: true }),
    false,
  )
  assert.equal(
    shouldRetryPaginatorWarmup({ ...warmup, sawNativeRelocation: true }),
    false,
  )
  assert.equal(
    shouldRetryPaginatorWarmup({
      ...warmup,
      sawNativeReadyAfterBusy: false,
      retryWhenReady: true,
    }),
    true,
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
