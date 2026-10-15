import { post } from './navic-reader-helpers.js'

// Stateless support only. The exact successful delivery record remains private to
// NavicReaderRuntime; this module has no registry, retained source, or commit counter.
export const readerCurrentDestinationDeliveryRecord = (
  message, view, sessionId, currentView, currentSessionId,
) => {
  if (!view || view !== currentView || sessionId !== currentSessionId ||
      typeof sessionId !== 'string' || !sessionId || sessionId !== sessionId.trim() ||
      sessionId === 'foliate-unbound' ||
      !Number.isSafeInteger(message.destinationCommitSequence) ||
      message.destinationCommitSequence <= 0) return null
  // Only flat locator fields from the actual delivered message; no diagnostic/presentation
  // data, mutable relocation detail, query identity or synthesized semantic proof.
  const {
    href, cfi, progress, pageIndex, pageCount, chapterProgress, chapterPageIndex,
    chapterPageCount, tocTitle, rangeCfi, reason, fraction, size, tocItemLabel, pageItemLabel,
  } = message
  return {
    view, sessionId, renderer: view.renderer,
    // Subordinate, read-only layout custody from this exact Live renderer at the
    // successful delivery. No profile upgrade or material/presentation authority.
    resolvedLayout: view.renderer?.captureResolvedLayout?.() ?? null,
    commitSequence: message.destinationCommitSequence,
    locator: { href, cfi, progress, pageIndex, pageCount, chapterProgress,
      chapterPageIndex, chapterPageCount, tocTitle, rangeCfi, reason, fraction, size,
      tocItemLabel, pageItemLabel },
  }
}

export const readerValidateCurrentDestinationQuery = (
  command, source, view, sessionId, commitSequence,
) => {
  if (typeof command?.commandId !== 'string' || !command.commandId ||
      command.commandId !== command.commandId.trim() ||
      typeof command.foliateSessionId !== 'string' || !command.foliateSessionId ||
      command.foliateSessionId !== command.foliateSessionId.trim() ||
      command.foliateSessionId !== sessionId ||
      !source || !view || source.view !== view ||
      source.sessionId !== sessionId || source.commitSequence !== commitSequence) {
    throw new Error('Current destination unavailable')
  }
}

// Ready-stage rendering may resolve layout again without changing the delivered
// semantic destination. Refresh only from the same original Live renderer, with
// exact current CFI and chapter coordinates; never navigate or mint another commit.
const currentResolvedLayout = source => {
  if (source.view.renderer !== source.renderer) return undefined
  if (source.resolvedLayout?.isCurrent() === true) return source.resolvedLayout.layout
  const { cfi, chapterPageIndex, chapterPageCount } = source.locator
  if (typeof cfi !== 'string' || !cfi || source.view.lastLocation?.cfi !== cfi ||
      !Number.isInteger(chapterPageIndex) || !Number.isInteger(chapterPageCount)) return undefined
  const fresh = source.renderer?.captureResolvedLayout?.()
  if (fresh?.isCurrent() !== true) return undefined
  const layout = fresh.layout
  if (layout.pageIndex !== chapterPageIndex || layout.pageCount !== chapterPageCount) return undefined
  const previous = source.resolvedLayout?.layout
  if (previous && (layout.viewGeneration !== previous.viewGeneration ||
      layout.navigationGeneration !== previous.navigationGeneration ||
      layout.sectionIndex !== previous.sectionIndex)) return undefined
  return layout
}

export const readerPostCurrentDestinationReply = (command, source) => {
  const delivered = post({
    type: 'currentDestinationResolved',
    commandId: command.commandId,
    foliateSessionId: source.sessionId,
    destinationFoliateSessionId: source.sessionId,
    destinationCommitSequence: source.commitSequence,
    ...source.locator,
    // A still-current numeric layout snapshot only. Never serialize the closure,
    // renderer, view, document, font set or content. Semantic replies without layout
    // remain semantic replies; a material allocator must independently require it.
    resolvedLayout: currentResolvedLayout(source),
  })
  if (!delivered) throw new Error('Current destination unavailable')
  return true
}
