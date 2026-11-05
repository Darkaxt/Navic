// Closed observations only. Logger-arrival timestamps are not renderer execution
// timestamps, readiness, command success, currentness or transition-timer authority.
const labels = new Set([
  'open-view-started', 'open-view-completed',
  'open-cover-started', 'open-cover-completed',
  'open-navigation-started', 'open-navigation-completed',
  'open-pagination-started', 'open-pagination-completed',
])

export const readerObserveOpenPhase = label => {
  if (!labels.has(label)) return
  try { console.log('[NavicReader]', label) } catch { /* Diagnostics cannot fail opening. */ }
}
