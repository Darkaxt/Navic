export function projectPaginatorCommitReceiptTrace(entry) {
  if (entry?.type !== 'page-turn:exact-settled') return null
  const pageIndex = Number(entry?.payload?.pageIndex)
  if (!Number.isSafeInteger(pageIndex) || pageIndex < 0) {
    return Object.freeze({ state: 'malformed', pageIndex: null })
  }
  return Object.freeze({ state: 'accepted', pageIndex })
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
