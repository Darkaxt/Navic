export function projectPaginatorCommitReceiptTrace(entry) {
  if (entry?.type !== 'page-turn:exact-settled') return null
  const pageIndex = Number(entry?.payload?.pageIndex)
  if (!Number.isSafeInteger(pageIndex) || pageIndex < 0) {
    return Object.freeze({ state: 'malformed', pageIndex: null })
  }
  return Object.freeze({ state: 'accepted', pageIndex })
}

const PaginatorNativeTapActions = new Set([
  'RIGHT',
  'LEFT',
  'NEXT',
  'PREVIOUS',
  'MENU',
])
const PaginatorNativeTerminalOutcomes = new Set([
  'CommittedForward',
  'CommittedBackward',
  'CompletedTapAction',
  'CancelledByUser',
  'CancelledLifecycle',
  'RejectedPreparing',
  'RejectedSettling',
  'RejectedDirection',
  'RejectedBoundary',
  'RejectedRendererUnavailable',
  'FailedRenderer',
  'FailedRecovery',
])
const PaginatorNativeReadinessStates = new Set([
  'BlockingInitialPreparation',
  'Ready',
  'Settling',
  'BackgroundPrefetch',
  'RefillingWorkingSet',
  'BlockingProfileRegeneration',
  'Failed',
])

const PaginatorNativeRelocationStates = new Set([
  'Queued',
  'Dispatched',
  'Acknowledged',
  'AwaitingVisualHandoff',
  'Completed',
  'Rejected',
])
const PaginatorNativeRelocationRejections = new Set([
  'None',
  'CommitPublicationFailed',
  'QueueInvalidated',
  'AcknowledgementTimeout',
  'JavascriptDispatchFailed',
  'ContentRejected',
])

export function projectPaginatorNativeLogLine(line) {
  if (typeof line !== 'string') return null
  const timestamp = Number(line.match(/^\s*(\d+(?:\.\d+)?)/)?.[1])
  if (!Number.isFinite(timestamp) || timestamp < 0) return null

  const tapAction = line.match(/Reader native tap action=([A-Z_]+)\b/)?.[1]
  if (PaginatorNativeTapActions.has(tapAction)) {
    return Object.freeze({ timestamp, type: 'tap', action: tapAction })
  }

  const terminal = line.match(
    /Reader gesture terminal gestureId=\d+ outcome=([A-Za-z]+) won=(true|false)\b/,
  )
  if (terminal && PaginatorNativeTerminalOutcomes.has(terminal[1])) {
    return Object.freeze({
      timestamp,
      type: 'terminal',
      outcome: terminal[1],
      won: terminal[2] === 'true',
    })
  }

  const readiness = line.match(
    /Readiness transition .* interaction=[A-Za-z]+->([A-Za-z]+) reason=/,
  )?.[1]
  if (PaginatorNativeReadinessStates.has(readiness)) {
    return Object.freeze({ timestamp, type: 'readiness', state: readiness })
  }

  const relocation = line.match(
    /\btarget=(\d+)\b.*\bstate=([A-Za-z]+) rejectionReason=([A-Za-z]+)\b/,
  )
  if (
    line.includes('reader-relocation ') &&
    relocation &&
    PaginatorNativeRelocationStates.has(relocation[2]) &&
    PaginatorNativeRelocationRejections.has(relocation[3])
  ) {
    const pageIndex = Number(relocation[1])
    if (!Number.isSafeInteger(pageIndex) || pageIndex < 0) return null
    return Object.freeze({
      timestamp,
      type: 'relocation',
      pageIndex,
      state: relocation[2],
      rejectionReason: relocation[3],
    })
  }

  if (
    line.includes(
      'PlayLikeCurl visual handoff content validation failed reason=ContentRejected',
    )
  ) {
    return Object.freeze({
      timestamp,
      type: 'handoff-failure',
      reason: 'ContentRejected',
    })
  }
  return null
}

export function shouldRetryPaginatorWarmup(state) {
  return (
    state?.sawNativeTap === true &&
    state?.readiness === 'Ready' &&
    (state?.retryWhenReady === true || state?.sawNativeReadyAfterBusy === true) &&
    state?.committedNativeTurn !== true &&
    state?.sawNativeRelocation !== true
  )
}

export function createPaginatorCommitReceiptTraceSink(
  maximumEntries = 64,
  project = projectPaginatorCommitReceiptTrace,
) {
  const limit = Math.max(1, Math.floor(Number(maximumEntries) || 1))
  const entries = []
  return {
    entries,
    droppedReceiptCount: 0,
    malformedReceiptCount: 0,
    push(entry) {
      const projected = project(entry)
      if (!projected) return entries.length
      if (projected.state === 'malformed') this.malformedReceiptCount += 1
      if (entries.length >= limit) {
        entries.shift()
        this.droppedReceiptCount += 1
      }
      entries.push(projected)
      return entries.length
    },
  }
}

export function validatePaginatorCommitReceiptSummary(summary, options = {}) {
  const expectedCount = Math.max(1, Math.floor(Number(options.expectedCount) || 20))
  const requireChapterTransition = options.requireChapterTransition === true
  if (summary?.state !== 'passed') throw new Error('acceptance did not pass')
  if (summary.acceptedForwardSettlements !== expectedCount) {
    throw new Error('accepted settlement count mismatch')
  }
  if (summary.terminalState !== 'none') throw new Error('terminal state was reported')
  if (summary.droppedReceiptCount !== 0) throw new Error('receipt trace overflowed')
  if (summary.malformedReceiptCount !== 0) throw new Error('malformed receipt was reported')
  if (requireChapterTransition && !Array.isArray(summary.chapterTransitions)) {
    throw new Error('chapter transition evidence is missing')
  }
  if (requireChapterTransition && summary.chapterTransitions.length < 1) {
    throw new Error('no chapter transition was observed')
  }
  return true
}
