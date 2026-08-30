import { readerDrawWhispersyncCueOrdinal } from './navic-reader-overlay-paint.js'

const DefaultHoldDurationMs = 1000
const DefaultTouchSlopPx = 10
const RenderedOrdinalEvidenceLimit = 32
const CueKeyPrefix = 'navic-whispersync-cue-map-'

const finiteOrdinal = value => {
  const ordinal = Number(value)
  return Number.isSafeInteger(ordinal) && ordinal >= 0 ? ordinal : null
}

const terminalPresentation = presentation => {
  const digest = String(presentation?.revisionDigest || '')
  const generation = Number(presentation?.presentationGeneration)
  const destination = presentation?.destinationCommitIdentity
  const foliateSessionId = String(destination?.foliateSessionId || '')
  const commitSequence = Number(destination?.commitSequence)
  return Boolean(
    /^[0-9a-f]{12}$/.test(digest) &&
    Number.isSafeInteger(generation) && generation > 0 &&
    foliateSessionId &&
    Number.isSafeInteger(commitSequence) && commitSequence > 0
  )
}

const trustedPresentation = presentation => Boolean(
  presentation?.enabled === true &&
  terminalPresentation(presentation) &&
  Array.isArray(presentation?.cues)
)

const sameLifecycleIdentity = (left, right) => {
  if (!left || !right) return false
  const leftDestination = left.destinationCommitIdentity
  const rightDestination = right.destinationCommitIdentity
  return left.revisionDigest === right.revisionDigest &&
    left.presentationGeneration === right.presentationGeneration &&
    leftDestination?.foliateSessionId === rightDestination?.foliateSessionId &&
    leftDestination?.commitSequence === rightDestination?.commitSequence
}

const samePresentation = (left, right) => {
  try {
    return JSON.stringify(left) === JSON.stringify(right)
  } catch (_) {
    return false
  }
}

const cancellationOutcome = reason => {
  const value = String(reason || '').replace(/^cancelled-/, '')
  return new Map([
    ['early-release', 'cancelled-early-release'],
    ['movement', 'cancelled-movement'],
    ['pointer', 'cancelled-pointer'],
    ['chrome-interception', 'cancelled-chrome-interception'],
    ['curl-start', 'cancelled-curl-start'],
    ['generation-replacement', 'cancelled-generation-replacement'],
  ]).get(value) || null
}

const rangeOrder = (left, right) => {
  const leftIndex = Number(left.content?.index)
  const rightIndex = Number(right.content?.index)
  if (Number.isFinite(leftIndex) && Number.isFinite(rightIndex) && leftIndex !== rightIndex) {
    return leftIndex - rightIndex
  }
  if (left.content !== right.content) return left.inputOrder - right.inputOrder
  try {
    return left.range.compareBoundaryPoints(0, right.range)
  } catch (_) {
    return left.inputOrder - right.inputOrder
  }
}

export class ReaderWhispersyncCueMapRuntime {
  constructor({
    contentEntries,
    resolveRange,
    resolveAnchorReceipt,
    postEvent,
    nativePointerOwnership = false,
    holdDurationMs = DefaultHoldDurationMs,
    touchSlopPx = DefaultTouchSlopPx,
  }) {
    this.contentEntries = contentEntries
    this.resolveRange = resolveRange
    this.resolveAnchorReceipt = resolveAnchorReceipt
    this.postEvent = postEvent
    this.nativePointerOwnership = nativePointerOwnership === true
    this.holdDurationMs = Math.max(1, Number(holdDurationMs) || DefaultHoldDurationMs)
    this.touchSlopPx = Math.max(0, Number(touchSlopPx) || DefaultTouchSlopPx)
    this.presentation = null
    this.deferredPresentation = null
    this.markers = []
    this.markerReceipts = []
    this.hold = null
    this.transportAcknowledgementPending = false
    this.pendingSourceOrdinal = null
  }

  replace(presentation) {
    if (!trustedPresentation(presentation)) {
      this.clear()
      this.postRendered([], [], presentation)
      return false
    }
    if (sameLifecycleIdentity(this.presentation, presentation)) {
      if (samePresentation(this.presentation, presentation)) {
        this.postRenderedSnapshot()
        return true
      }
      if (this.hold && !this.hold.completed) {
        this.deferredPresentation = presentation
        this.postRenderedSnapshot()
        return true
      }
      this.endHold(null)
      this.installPresentation(presentation)
      return true
    }

    this.deferredPresentation = null
    this.endHold('cancelled-generation-replacement')
    this.installPresentation(presentation)
    return true
  }

  installPresentation(presentation) {
    this.clearMarkers()
    this.presentation = presentation
    this.transportAcknowledgementPending = presentation.transportAcknowledgementPending === true
    this.pendingSourceOrdinal = this.transportAcknowledgementPending
      ? finiteOrdinal(presentation.requestedSourceOrdinal)
      : null

    const mapped = []
    const contents = Array.from(this.contentEntries?.() || [])
    presentation.cues.forEach((cue, inputOrder) => {
      const sourceOrdinal = finiteOrdinal(cue?.sourceOrdinal)
      if (sourceOrdinal == null) return
      for (const content of contents) {
        let range = null
        try {
          range = this.resolveRange?.(content, cue) || null
        } catch (_) {
          range = null
        }
        if (!range || range.collapsed) continue
        mapped.push({ content, cue, range, sourceOrdinal, inputOrder })
        break
      }
    })
    mapped.sort(rangeOrder)
    mapped.forEach(item => this.paint(item))
    const markerReceipts = mapped.map(item => {
      let anchorReceipt = null
      try {
        anchorReceipt = this.resolveAnchorReceipt?.(item.content, item.cue, item.range) || null
      } catch (_) {
        anchorReceipt = null
      }
      if (!anchorReceipt) return null
      return {
        sourceOrdinal: item.sourceOrdinal,
        prepared: item.sourceOrdinal === finiteOrdinal(this.presentation.preparedSourceOrdinal),
        requested: item.sourceOrdinal === finiteOrdinal(this.presentation.requestedSourceOrdinal),
        audioActive: item.sourceOrdinal === finiteOrdinal(this.presentation.audioActiveSourceOrdinal),
        renderedHighlight: item.sourceOrdinal === finiteOrdinal(
          this.presentation.renderedHighlightSourceOrdinal
        ),
        anchorReceipt,
      }
    }).filter(Boolean)
    this.markerReceipts = markerReceipts
    this.postRendered(mapped.map(item => item.sourceOrdinal), markerReceipts)
  }

  flushDeferredPresentation(pendingSourceOrdinal = null) {
    const deferred = this.deferredPresentation
    this.deferredPresentation = null
    if (!deferred || !sameLifecycleIdentity(this.presentation, deferred)) return false
    const presentation = pendingSourceOrdinal == null
      ? deferred
      : {
          ...deferred,
          requestedSourceOrdinal: pendingSourceOrdinal,
          transportAcknowledgementPending: true,
        }
    this.installPresentation(presentation)
    return true
  }

  clear() {
    this.deferredPresentation = null
    this.endHold('cancelled-generation-replacement')
    this.clearMarkers()
    this.presentation = null
    this.transportAcknowledgementPending = false
    this.pendingSourceOrdinal = null
  }

  clearMarkers() {
    for (const marker of this.markers) marker.content?.overlayer?.remove?.(marker.key)
    this.markers = []
    this.markerReceipts = []
  }

  paint(item) {
    const overlayer = item.content?.overlayer
    if (!overlayer?.add) return
    const generation = this.presentation.presentationGeneration
    const key = `${CueKeyPrefix}${generation}-${item.sourceOrdinal}`
    const record = { ...item, key, marker: null }
    const visualOptions = {
      sourceOrdinal: item.sourceOrdinal,
      prepared: item.sourceOrdinal === finiteOrdinal(this.presentation.preparedSourceOrdinal),
      requested: item.sourceOrdinal === finiteOrdinal(this.presentation.requestedSourceOrdinal),
      audioActive: item.sourceOrdinal === finiteOrdinal(this.presentation.audioActiveSourceOrdinal),
      renderedHighlight: item.sourceOrdinal === finiteOrdinal(this.presentation.renderedHighlightSourceOrdinal),
    }
    const draw = rects => {
      const marker = readerDrawWhispersyncCueOrdinal(rects, visualOptions)
      record.marker = marker
      this.decorateMarker(marker, item.sourceOrdinal)
      this.attachHoldEvents(marker, item.sourceOrdinal)
      return marker
    }
    overlayer.add(key, item.range, draw, visualOptions)
    if (record.marker) this.markers.push(record)
  }

  decorateMarker(marker, sourceOrdinal) {
    marker.dataset.navicCueSourceOrdinal = String(sourceOrdinal)
    marker.dataset.navicCueMapped = 'true'
    marker.dataset.navicCuePrepared = String(sourceOrdinal === finiteOrdinal(this.presentation.preparedSourceOrdinal))
    marker.dataset.navicCueRequested = String(sourceOrdinal === finiteOrdinal(this.presentation.requestedSourceOrdinal))
    marker.dataset.navicCueAudioActive = String(sourceOrdinal === finiteOrdinal(this.presentation.audioActiveSourceOrdinal))
    marker.dataset.navicCueRenderedHighlight = String(
      sourceOrdinal === finiteOrdinal(this.presentation.renderedHighlightSourceOrdinal)
    )
    const pending = this.transportAcknowledgementPending && sourceOrdinal === this.pendingSourceOrdinal
    marker.dataset.navicCueHoldState = pending ? 'indeterminate' : 'idle'
    if (this.nativePointerOwnership) marker.style.pointerEvents = 'none'
    this.setPendingRing(marker, pending)
  }

  attachHoldEvents(marker, sourceOrdinal) {
    if (this.nativePointerOwnership) return
    marker.addEventListener('pointerdown', event => this.beginHold(event, marker, sourceOrdinal))
    marker.addEventListener('lostpointercapture', event => this.pointerCancelled(event))
  }

  beginHold(event, marker, sourceOrdinal) {
    if (!this.presentation || this.transportAcknowledgementPending || this.hold) return
    if (event.button != null && event.button !== 0) return
    event.preventDefault?.()
    event.stopPropagation?.()
    const pointerId = Number(event.pointerId)
    const ownerDocument = marker.ownerDocument || document
    const hold = {
      pointerId,
      sourceOrdinal,
      marker,
      ownerDocument,
      startX: Number(event.clientX) || 0,
      startY: Number(event.clientY) || 0,
      completed: false,
      captureAcquired: false,
      timer: null,
      onPointerMove: null,
      onPointerUp: null,
      onPointerCancel: null,
    }
    hold.onPointerMove = current => this.moveHold(current)
    hold.onPointerUp = current => this.releaseHold(current)
    hold.onPointerCancel = current => this.pointerCancelled(current)
    ownerDocument.addEventListener('pointermove', hold.onPointerMove, true)
    ownerDocument.addEventListener('pointerup', hold.onPointerUp, true)
    ownerDocument.addEventListener('pointercancel', hold.onPointerCancel, true)
    this.hold = hold
    try {
      marker.setPointerCapture?.(pointerId)
      hold.captureAcquired = typeof marker.setPointerCapture === 'function'
    } catch (_) {
      hold.captureAcquired = false
    }
    hold.timer = setTimeout(() => this.completeHold(hold), this.holdDurationMs)
    marker.dataset.navicCueHoldState = 'holding'
    this.startDeterminateRing(marker)
  }

  moveHold(event) {
    const hold = this.matchingHold(event)
    if (!hold || hold.completed) return
    const deltaX = (Number(event.clientX) || 0) - hold.startX
    const deltaY = (Number(event.clientY) || 0) - hold.startY
    if (Math.hypot(deltaX, deltaY) > this.touchSlopPx) this.cancelHold('movement')
  }

  releaseHold(event) {
    const hold = this.matchingHold(event)
    if (!hold) return
    event.preventDefault?.()
    event.stopPropagation?.()
    this.endHold(hold.completed ? null : 'cancelled-early-release')
  }

  pointerCancelled(event) {
    if (this.matchingHold(event)) this.endHold('cancelled-pointer')
  }

  matchingHold(event) {
    if (!this.hold) return null
    return Number(event?.pointerId) === this.hold.pointerId ? this.hold : null
  }

  completeHold(hold) {
    if (this.hold !== hold || hold.completed || !this.presentation) return
    hold.completed = true
    hold.timer = null
    this.transportAcknowledgementPending = true
    this.pendingSourceOrdinal = hold.sourceOrdinal
    hold.marker.dataset.navicCueHoldState = 'indeterminate'
    this.finishDeterminateRing(hold.marker)
    this.setPendingRing(hold.marker, true)
    this.postHoldOutcome(hold.sourceOrdinal, 'completed')
    this.postSafeEvent({
      type: 'whispersyncCueMapSeekRequested',
      sourceOrdinal: hold.sourceOrdinal,
      ...this.destinationProof(),
    })
    if (this.deferredPresentation) {
      this.deferredPresentation = {
        ...this.deferredPresentation,
        requestedSourceOrdinal: hold.sourceOrdinal,
        transportAcknowledgementPending: true,
      }
      this.endHold(null)
    }
  }

  cancelHold(reason) {
    const outcome = cancellationOutcome(reason)
    if (!outcome) return false
    return this.endHold(outcome)
  }

  endHold(outcome = null) {
    const hold = this.hold
    if (!hold) return false
    const incomplete = !hold.completed
    this.hold = null
    if (hold.timer != null) clearTimeout(hold.timer)
    hold.ownerDocument.removeEventListener('pointermove', hold.onPointerMove, true)
    hold.ownerDocument.removeEventListener('pointerup', hold.onPointerUp, true)
    hold.ownerDocument.removeEventListener('pointercancel', hold.onPointerCancel, true)
    if (hold.captureAcquired) {
      try {
        hold.marker.releasePointerCapture?.(hold.pointerId)
      } catch (_) {
        // Capture may already have been lost; document listeners still close the hold.
      }
    }
    if (incomplete) {
      hold.marker.dataset.navicCueHoldState = 'idle'
      this.resetDeterminateRing(hold.marker)
      if (outcome) this.postHoldOutcome(hold.sourceOrdinal, outcome)
    }
    if (this.deferredPresentation) this.flushDeferredPresentation()
    return true
  }

  startDeterminateRing(marker) {
    const ring = marker.querySelector('[data-navic-cue-hold-ring="determinate"]')
    if (!ring) return
    ring.dataset.navicCueHoldProgressState = 'running'
    ring.setAttribute('opacity', '1')
    ring.style.transition = 'none'
    const circumference = ring.getAttribute('stroke-dasharray')
    ring.setAttribute('stroke-dashoffset', circumference)
    requestAnimationFrame(() => {
      if (this.hold?.marker !== marker || this.hold.completed) return
      ring.style.transition = `stroke-dashoffset ${this.holdDurationMs}ms linear`
      ring.setAttribute('stroke-dashoffset', '0')
    })
  }

  finishDeterminateRing(marker) {
    const ring = marker.querySelector('[data-navic-cue-hold-ring="determinate"]')
    if (!ring) return
    ring.dataset.navicCueHoldProgressState = 'completed'
    ring.style.transition = 'none'
    ring.setAttribute('stroke-dashoffset', '0')
    ring.setAttribute('opacity', '0')
  }

  resetDeterminateRing(marker) {
    const ring = marker.querySelector('[data-navic-cue-hold-ring="determinate"]')
    if (!ring) return
    ring.dataset.navicCueHoldProgressState = 'idle'
    ring.style.transition = 'none'
    ring.setAttribute('stroke-dashoffset', ring.getAttribute('stroke-dasharray'))
    ring.setAttribute('opacity', '0')
  }

  setPendingRing(marker, visible) {
    const ring = marker.querySelector('[data-navic-cue-hold-ring="indeterminate"]')
    if (!ring) return
    ring.dataset.navicCueHoldRingVisible = String(Boolean(visible))
    ring.setAttribute('opacity', visible ? '1' : '0')
  }

  postRendered(sourceOrdinals, markerReceipts = [], presentation = this.presentation) {
    this.postSafeEvent({
      type: 'whispersyncCueMapRendered',
      sourceOrdinals: sourceOrdinals.slice(0, RenderedOrdinalEvidenceLimit),
      markerReceipts: markerReceipts.slice(0, RenderedOrdinalEvidenceLimit),
      ...this.destinationProof(presentation),
    }, presentation)
  }

  postRenderedSnapshot() {
    this.postRendered(
      this.markers.map(marker => marker.sourceOrdinal),
      this.markerReceipts
    )
  }

  postHoldOutcome(sourceOrdinal, outcome) {
    this.postSafeEvent({ type: 'whispersyncCueMapHoldOutcome', sourceOrdinal, outcome })
  }

  destinationProof(presentation = this.presentation) {
    const destination = presentation?.destinationCommitIdentity
    return {
      destinationFoliateSessionId: String(destination?.foliateSessionId || ''),
      destinationCommitSequence: Number(destination?.commitSequence),
    }
  }

  postSafeEvent(event, presentation = this.presentation) {
    if (!terminalPresentation(presentation)) return
    this.postEvent?.({
      ...event,
      revisionDigest: presentation.revisionDigest,
      presentationGeneration: presentation.presentationGeneration,
    })
  }
}
