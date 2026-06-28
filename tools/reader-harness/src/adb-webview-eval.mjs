import { spawnSync } from 'node:child_process'

const args = new Map()
for (let index = 2; index < process.argv.length; index += 1) {
  const arg = process.argv[index]
  if (!arg.startsWith('--')) continue
  const key = arg.slice(2)
  const value = process.argv[index + 1]?.startsWith('--') ? '' : process.argv[index + 1]
  args.set(key, value ?? '')
  if (value !== undefined && value !== '') index += 1
}

const packageName = args.get('package') || 'darkaxt.navic.readerdev'
const deviceSerial = args.get('device') || ''
const probe = args.get('probe') || 'internal-link-native'
const localPort = args.get('local-port') || '9223'
const adbForwardCommand = 'adb forward'

function adbArgs(argumentsList) {
  return deviceSerial ? ['-s', deviceSerial, ...argumentsList] : argumentsList
}

function runAdb(argumentsList) {
  const fullArgs = adbArgs(argumentsList)
  const result = spawnSync('adb', fullArgs, { encoding: 'utf8' })
  if (result.status !== 0) {
    const stderr = result.stderr?.trim()
    const stdout = result.stdout?.trim()
    throw new Error(`adb ${fullArgs.join(' ')} failed with exit code ${result.status}\n${stderr || stdout}`)
  }
  return result.stdout.trim()
}

async function findReaderPage(port) {
  const response = await fetch(`http://127.0.0.1:${port}/json/list`)
  if (!response.ok) {
    throw new Error(`Could not list WebView targets: HTTP ${response.status}`)
  }
  const pages = await response.json()
  const readerPage = pages.find(page => page.url?.includes('/assets/reader/index.html')) ??
    pages.find(page => page.url?.startsWith('https://appassets.androidplatform.net/')) ??
    pages.find(page => page.title?.includes('Navic'))
  if (!readerPage?.webSocketDebuggerUrl) {
    const pageDescriptions = pages.map(page => `${page.title} ${page.url}`).join('\n')
    throw new Error(`Could not find Navic Reader WebView page. Pages:\n${pageDescriptions}`)
  }
  return readerPage
}

function createCdpClient(webSocketDebuggerUrl) {
  const socket = new WebSocket(webSocketDebuggerUrl)
  let nextId = 1
  const pending = new Map()

  const opened = new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })

  socket.addEventListener('message', event => {
    const payload = JSON.parse(event.data)
    if (!payload.id || !pending.has(payload.id)) return
    const callbacks = pending.get(payload.id)
    pending.delete(payload.id)
    if (payload.error) {
      callbacks.reject(new Error(`${payload.error.message}: ${payload.error.data || ''}`.trim()))
    } else {
      callbacks.resolve(payload.result)
    }
  })
  socket.addEventListener('close', () => {
    for (const callbacks of pending.values()) {
      callbacks.reject(new Error('CDP WebSocket closed before response'))
    }
    pending.clear()
  })

  return {
    async send(method, params = {}) {
      await opened
      const id = nextId++
      const response = new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject })
      })
      socket.send(JSON.stringify({ id, method, params }))
      return response
    },
    close() {
      socket.close()
    },
  }
}

async function evaluateOnPage(page, expression) {
  const client = createCdpClient(page.webSocketDebuggerUrl)
  try {
    await client.send('Runtime.enable')
    const evaluation = await client.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
    })
    if (evaluation.exceptionDetails) {
      throw new Error(`Runtime.evaluate failed: ${JSON.stringify(evaluation.exceptionDetails)}`)
    }
    return evaluation.result?.value
  } finally {
    client.close()
  }
}

async function runInternalLinkNativeProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    if (!window.NavicReaderBridge?.dispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    await window.NavicReaderBridge.dispatch({
      type: 'applySettings',
      settings: { nativeTapZones: true },
    })
    const href = '#navic-adb-internal-link-probe'
    const event = new CustomEvent('link', {
      bubbles: true,
      cancelable: true,
      detail: { href, a: null },
    })
    const dispatched = view.dispatchEvent(event)
    return {
      probe: 'internal-link-native',
      href,
      dispatched,
      defaultPrevented: event.defaultPrevented,
      expectedSource: 'native-short-tap',
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function runPhase3EventsProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    if (!window.NavicReaderBridge?.dispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const events = []
    const diagnostics = {
      contentEntryCount: 0,
      selectedContentIndex: null,
      scrolledEdgeListenerAttachedBeforeLoad: false,
      scrolledEdgeListenerAttachedAfterLoad: false,
      syntheticTouchEventsDispatched: false,
      rendererBeforeOverride: null,
      rendererDuringOverride: null,
    }
    const externalAnchor = document.createElement('a')
    externalAnchor.setAttribute('href', '../Text/chapter-01.xhtml#note')
    const externalLink = new CustomEvent('external-link', {
      bubbles: true,
      cancelable: true,
      detail: {
        href: 'https://example.test/navic-external-probe',
        a: externalAnchor,
      },
    })
    view.dispatchEvent(externalLink)
    events.push({
      type: 'external-link',
      defaultPrevented: externalLink.defaultPrevented,
    })

    const annotationValue = 'epubcfi(/6/8!/4/2:12)'
    view.dispatchEvent(new CustomEvent('draw-annotation', {
      bubbles: true,
      detail: {
        index: 3,
        value: annotationValue,
        range: null,
      },
    }))
    events.push({ type: 'draw-annotation' })

    view.dispatchEvent(new CustomEvent('show-annotation', {
      bubbles: true,
      detail: {
        index: 3,
        value: annotationValue,
        range: null,
      },
    }))
    events.push({ type: 'show-annotation' })

    view.dispatchEvent(new CustomEvent('create-overlay', {
      bubbles: true,
      detail: { index: 3 },
    }))
    events.push({ type: 'create-overlay' })

    view.dispatchEvent(new CustomEvent('load', {
      bubbles: true,
      detail: { index: 0 },
    }))
    events.push({ type: 'load' })

    view.history?.dispatchEvent?.(new Event('index-change'))
    events.push({ type: 'pushState' })

    const contentEntries = view.renderer?.getContents?.()?.filter?.(entry => entry?.doc?.body) || []
    diagnostics.contentEntryCount = contentEntries.length
    let contentEntry = contentEntries.find(entry => entry.doc.defaultView?.__navicScrolledEdgeTurnGesturesAttached) ||
      contentEntries[0]
    let contentDoc = contentEntry?.doc
    diagnostics.selectedContentIndex = Number.isFinite(contentEntry?.index) ? contentEntry.index : null
    diagnostics.scrolledEdgeListenerAttachedBeforeLoad =
      contentDoc?.defaultView?.__navicScrolledEdgeTurnGesturesAttached === true
    if (contentDoc?.body && !contentDoc.defaultView?.__navicScrolledEdgeTurnGesturesAttached) {
      view.dispatchEvent(new CustomEvent('load', {
        bubbles: true,
        detail: {
          doc: contentDoc,
          index: Number.isFinite(contentEntry?.index) ? contentEntry.index : 0,
        },
      }))
      contentEntry = contentEntries.find(entry => entry.doc.defaultView?.__navicScrolledEdgeTurnGesturesAttached) ||
        contentEntry
      contentDoc = contentEntry?.doc
      diagnostics.selectedContentIndex = Number.isFinite(contentEntry?.index) ? contentEntry.index : null
    }
    diagnostics.scrolledEdgeListenerAttachedAfterLoad =
      contentDoc?.defaultView?.__navicScrolledEdgeTurnGesturesAttached === true
    if (contentDoc?.body) {
      const marker = contentDoc.createElement('span')
      marker.className = 'navic-active-overlay-fragment'
      marker.textContent = 'navic-footnote-close-probe'
      contentDoc.body.appendChild(marker)
      await window.NavicReaderBridge.dispatch({ type: 'clearOverlay' })
      events.push({ type: 'footnoteClose' })

      const pullUpResult = await Promise.resolve(window.NavicReaderBridge.dispatch({
        type: 'diagnosticScrolledEdgePullUp',
      }))
      diagnostics.diagnosticScrolledEdgePullUp = pullUpResult
      if (!pullUpResult?.posted) {
        throw new Error(`diagnosticScrolledEdgePullUp did not post pullUp; result=${JSON.stringify(pullUpResult)}`)
      }
      events.push({ type: 'pullUp', result: pullUpResult })
    }

    return {
      probe: 'phase3-events',
      events,
      diagnostics,
      expectedLogLabels: [
        'Reader bridge event: externalLink',
        'Reader bridge event: annotationDrawn',
        'Reader bridge event: annotationClick',
        'Reader bridge event: overlayCreated',
        'Reader bridge event: loadDoc',
        'Reader bridge event: pushState',
        'Reader bridge event: footnoteClose',
        'Reader bridge event: pullUp',
      ],
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function runAnnotationRoundTripProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    if (!window.NavicReaderBridge?.dispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const originalAddAnnotation = view.addAnnotation
    const annotationValue = 'epubcfi(/6/8!/4/2:12)'
    const noteText = 'Navic annotation roundtrip note'
    const events = []
    const diagnostics = {
      capturedAddAnnotation: null,
      noteMarkerCreated: false,
      noteMarkerTagName: null,
      noteMarkerChildCount: null,
      clickValue: null,
      clickIndex: null,
    }

    try {
      view.addAnnotation = async (annotation, removeExisting) => {
        diagnostics.capturedAddAnnotation = {
          id: annotation?.id || '',
          value: annotation?.value || '',
          color: annotation?.color || '',
          note: annotation?.note || '',
          removeExisting: removeExisting === true,
        }
        view.dispatchEvent(new CustomEvent('draw-annotation', {
          bubbles: true,
          detail: {
            index: 5,
            value: annotation?.value || '',
            annotation,
            range: null,
            draw(drawer, options) {
              const node = drawer(
                [{
                  left: 2,
                  top: 4,
                  right: 42,
                  bottom: 14,
                  width: 40,
                  height: 10,
                }],
                options,
              )
              diagnostics.noteMarkerCreated =
                node?.getAttribute?.('data-navic-note-annotation') === 'true' ||
                node?.querySelector?.('[data-navic-note-annotation]') != null
              diagnostics.noteMarkerTagName = node?.tagName || null
              diagnostics.noteMarkerChildCount = node?.childNodes?.length ?? null
              return node
            },
          },
        }))
        events.push({ type: 'draw-annotation' })

        view.dispatchEvent(new CustomEvent('show-annotation', {
          bubbles: true,
          detail: {
            index: 5,
            value: annotation?.value || '',
            annotation,
            range: null,
          },
        }))
        diagnostics.clickValue = annotation?.value || ''
        diagnostics.clickIndex = 5
        events.push({ type: 'show-annotation' })
      }

      await window.NavicReaderBridge.dispatch({
        type: 'applyHighlights',
        highlights: [{
          id: 'navic-annotation-roundtrip-probe',
          cfi: annotationValue,
          color: '#f4d35e',
          note: 'Navic annotation roundtrip note',
        }],
      })
    } finally {
      view.addAnnotation = originalAddAnnotation
    }

    if (diagnostics.capturedAddAnnotation?.note !== noteText) {
      throw new Error(`Annotation note was not passed to addAnnotation: ${JSON.stringify(diagnostics.capturedAddAnnotation)}`)
    }
    if (diagnostics.capturedAddAnnotation?.value !== annotationValue) {
      throw new Error(`Annotation CFI was not passed to addAnnotation: ${JSON.stringify(diagnostics.capturedAddAnnotation)}`)
    }
    if (!diagnostics.noteMarkerCreated) {
      throw new Error(`Note marker was not created: ${JSON.stringify(diagnostics)}`)
    }

    return {
      probe: 'annotation-roundtrip',
      events,
      diagnostics,
      expectedLogLabels: [
        'Reader bridge event: annotationDrawn',
        'Reader bridge event: annotationClick',
      ],
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function runHistoryControlsProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    if (!window.NavicReaderBridge?.dispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view?.history) {
      throw new Error('Missing foliate-view history')
    }
    view.history.clear?.()
    view.history.pushState({ fraction: 0.1, probe: 'navic-history-controls-start' })
    view.history.pushState({ fraction: 0.2, probe: 'navic-history-controls-current' })
    await new Promise(resolve => requestAnimationFrame(resolve))
    return {
      probe: 'history-controls',
      canGoBack: view.history.canGoBack === true,
      canGoForward: view.history.canGoForward === true,
      expectedNativeControls: ['History back', 'Close history controls'],
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function runSelectionPayloadProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    const content = view.renderer?.getContents?.()?.find?.(entry => entry?.doc)
    const doc = content?.doc
    if (!doc?.body) {
      throw new Error('Missing loaded content document')
    }
    const paragraph = doc.createElement('p')
    paragraph.setAttribute('data-navic-selection-probe', 'true')
    paragraph.setAttribute('role', 'doc-footnote')
    paragraph.textContent = 'Navic selection payload probe text for Anx selection parity.'
    doc.body.appendChild(paragraph)

    const range = doc.createRange()
    range.setStart(paragraph.firstChild, 0)
    range.setEnd(paragraph.firstChild, 31)
    const selection = doc.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    doc.dispatchEvent(new Event('selectionchange', { bubbles: true }))

    return {
      probe: 'selection-payload',
      selectedText: selection.toString(),
      role: paragraph.getAttribute('role'),
      expectedLogLabels: [
        'Reader bridge event: selectionChanged(footnote=true',
      ],
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function runRelocationPayloadProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const observed = []
    const observedPayloads = []
    window.NavicAndroidBridge.postMessage = message => {
      observed.push(message)
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const dispatchResult = readerBridgeDispatch({
        type: 'diagnosticLocationSnapshot',
        reason: 'adb-relocation-payload-probe',
      })
      let locationSnapshotResult = null
      try {
        locationSnapshotResult = await Promise.resolve(dispatchResult)
      } catch (error) {
        originalPostMessage(JSON.stringify({
          type: 'error',
          code: 'relocation_payload_probe_dispatch_failed',
          message: error?.message || String(error),
        }))
        throw error
      }
      const returnedLocation = locationSnapshotResult?.message?.type === 'locationChanged'
        ? locationSnapshotResult.message
        : null
      const locationChanged = observedPayloads.find(payload => payload.type === 'locationChanged') || returnedLocation
      if (!locationChanged) {
        throw new Error(
          `diagnosticLocationSnapshot did not emit locationChanged; result=${
            JSON.stringify(locationSnapshotResult)
          }; observedMessageCount=${observed.length}`
        )
      }

      return {
        probe: 'relocation-payload',
        reason: 'adb-relocation-payload-probe',
        locationSnapshotResult,
        observedLocation: locationChanged,
        observedMessageCount: observed.length,
        expectedLogLabels: [
          'Reader bridge event: locationChanged',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
    }
  }})()`)
}

async function runVisibleRangeProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }

    const observed = []
    const observedPayloads = []
    window.NavicAndroidBridge.postMessage = message => {
      observed.push(message)
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const dispatchResult = readerBridgeDispatch({
        type: 'diagnosticLocationSnapshot',
        reason: 'adb-visible-range-probe',
      })
      let locationSnapshotResult = null
      try {
        locationSnapshotResult = await Promise.resolve(dispatchResult)
      } catch (error) {
        originalPostMessage(JSON.stringify({
          type: 'error',
          code: 'visible_range_probe_dispatch_failed',
          message: error?.message || String(error),
        }))
        throw error
      }

      const returnedVisibleRange = locationSnapshotResult?.visibleTextRangeResult?.visibleRange
      const visibleRange = observedPayloads.find(payload => payload.type === 'visibleTextRange') || returnedVisibleRange
      if (!visibleRange) {
        throw new Error(
          `diagnosticLocationSnapshot did not emit visibleTextRange; result=${
            JSON.stringify(locationSnapshotResult)
          }; observedMessageCount=${observed.length}`
        )
      }

      return {
        probe: 'visible-range',
        reason: 'adb-visible-range-probe',
        locationSnapshotResult,
        observedVisibleRange: observedPayloads.find(payload => payload.type === 'visibleTextRange') || null,
        visibleRange,
        observedMessageCount: observed.length,
        expectedLogLabels: [
          'Reader bridge event: visibleTextRange',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
    }
  }})()`)
}

async function runWhispersyncAudioFollowProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }

    const observedPayloads = []
    const settleFrames = async (count = 2) => {
      for (let index = 0; index < count; index += 1) {
        await new Promise(resolve => requestAnimationFrame(resolve))
      }
    }
    const latestVisibleRange = startIndex => {
      for (let index = observedPayloads.length - 1; index >= startIndex; index -= 1) {
        const payload = observedPayloads[index]
        if (payload?.type === 'visibleTextRange') return payload
      }
      return null
    }

    window.NavicAndroidBridge.postMessage = message => {
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const targetHref = 'OEBPS/xhtml/Authorforeword.xhtml'
      await Promise.resolve(readerBridgeDispatch({
        type: 'goToHref',
        href: targetHref,
      }))
      await settleFrames(3)
      const currentSnapshot = await Promise.resolve(readerBridgeDispatch({
        type: 'diagnosticLocationSnapshot',
        reason: 'whispersync-audio-follow-probe-initial',
      }))
      const currentHref = String(currentSnapshot?.message?.href || '').trim()
      if (currentHref !== targetHref) {
        throw new Error(`Expected audio-follow probe to start on ${targetHref}, got ${currentHref}`)
      }

      readerBridgeDispatch({
        type: 'applyOverlayFragment',
        fragment: {
          textHref: targetHref,
          resourceHref: 'navic-whispersync-audio-follow-probe',
          clipBeginSeconds: 1,
          clipEndSeconds: 2,
          label: 'Whispersync audio follow probe',
        },
      })
      await settleFrames()

      const snapshotStartIndex = observedPayloads.length
      const followSnapshot = await Promise.resolve(readerBridgeDispatch({
        type: 'diagnosticLocationSnapshot',
        reason: 'media-overlay-follow',
      }))
      const returnedVisibleRange = followSnapshot?.visibleTextRangeResult?.visibleRange || null
      const visibleRange = latestVisibleRange(snapshotStartIndex) || returnedVisibleRange
      if (!visibleRange) {
        throw new Error(
          `Expected media-overlay-follow snapshot to emit visibleTextRange; result=${
            JSON.stringify(followSnapshot)
          }; observed=${JSON.stringify(observedPayloads.slice(snapshotStartIndex))}`
        )
      }
      if (visibleRange.source !== 'media-overlay-follow') {
        throw new Error(`Expected visibleTextRange source media-overlay-follow, got ${JSON.stringify(visibleRange)}`)
      }

      return {
        probe: 'whispersync-audio-follow',
        currentHref,
        targetHref,
        followSnapshot,
        visibleRange,
        observedVisibleRanges: observedPayloads.filter(payload => payload?.type === 'visibleTextRange'),
        expectedLogLabels: [
          'Reader bridge event: visibleTextRange',
          'source=media-overlay-follow',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
    }
  }})()`)
}

async function runWhispersyncPageScopedControlProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }

    const cueHref = 'OEBPS/xhtml/Authorforeword.xhtml'
    const unsupportedHref = 'OEBPS/xhtml/mini_toc.xhtml'
    const observedPayloads = []
    const settleFrames = async (count = 3) => {
      for (let index = 0; index < count; index += 1) {
        await new Promise(resolve => requestAnimationFrame(resolve))
      }
    }
    const latestVisibleRange = startIndex => {
      for (let index = observedPayloads.length - 1; index >= startIndex; index -= 1) {
        const payload = observedPayloads[index]
        if (payload?.type === 'visibleTextRange') return payload
      }
      return null
    }
    const jumpAndSnapshot = async (href, reason) => {
      const startIndex = observedPayloads.length
      await Promise.resolve(readerBridgeDispatch({
        type: 'goToHref',
        href,
      }))
      await settleFrames()
      const snapshot = await Promise.resolve(readerBridgeDispatch({
        type: 'diagnosticLocationSnapshot',
        reason,
      }))
      const visibleRange = latestVisibleRange(startIndex) ||
        snapshot?.visibleTextRangeResult?.visibleRange ||
        null
      if (!visibleRange) {
        throw new Error(
          `Expected ${reason} to emit visibleTextRange; result=${JSON.stringify(snapshot)}; observed=${
            JSON.stringify(observedPayloads.slice(startIndex))
          }`
        )
      }
      if (visibleRange.textHref !== href) {
        throw new Error(`Expected ${reason} visibleTextRange for ${href}, got ${JSON.stringify(visibleRange)}`)
      }
      if (visibleRange.source !== reason) {
        throw new Error(`Expected ${reason} visibleTextRange source, got ${JSON.stringify(visibleRange)}`)
      }
      return {
        href,
        reason,
        snapshot,
        visibleRange,
        observedPayloads: observedPayloads.slice(startIndex),
      }
    }

    window.NavicAndroidBridge.postMessage = message => {
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const cueCovered = await jumpAndSnapshot(cueHref, 'page-scoped-control-cue-covered')
      readerBridgeDispatch({
        type: 'applyOverlayFragment',
        fragment: {
          textHref: cueHref,
          resourceHref: '6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b',
          clipBeginSeconds: 263.36,
          clipEndSeconds: 282.92,
          label: 'Whispersync page-scoped control probe',
        },
      })
      await settleFrames()
      const unsupported = await jumpAndSnapshot(unsupportedHref, 'page-scoped-control-unsupported')

      return {
        probe: 'whispersync-page-scoped-control',
        cueHref,
        unsupportedHref,
        cueCovered,
        unsupported,
        observedVisibleRanges: observedPayloads.filter(payload => payload?.type === 'visibleTextRange'),
        observedOverlayFragments: observedPayloads.filter(payload => payload?.type === 'overlayFragmentActive'),
        expectedLogLabels: [
          'Reader bridge event: visibleTextRange',
          'page-scoped-control-cue-covered',
          'Whispersync audiobook seek',
          'positionMs=263360',
          'overlayFragmentActive',
          'page-scoped-control-unsupported',
          'Dispatching reader engine command: clearOverlay',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
    }
  }})()`)
}

async function runWhispersyncCharOffsetOverlayProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }

    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }

    const observedPayloads = []
    const settleFrames = async (count = 2) => {
      for (let index = 0; index < count; index += 1) {
        await new Promise(resolve => requestAnimationFrame(resolve))
      }
    }
    const latestPayload = (type, startIndex = 0) => {
      for (let index = observedPayloads.length - 1; index >= startIndex; index -= 1) {
        const payload = observedPayloads[index]
        if (payload?.type === type) return payload
      }
      return null
    }
    const rangeMarkers = () => {
      const contents = view?.renderer?.getContents?.() || []
      return contents.flatMap(content => {
        const href = String(
          view?.book?.sections?.[Math.floor(Number(content?.index))]?.href ||
          content?.href ||
          ''
        )
        return Array.from(content?.doc?.querySelectorAll?.('[data-navic-media-overlay-range="true"]') || [])
          .map(marker => ({
            href,
            text: marker.textContent || '',
            className: marker.className || '',
            textStart: marker.dataset.navicTextStart || '',
            textEnd: marker.dataset.navicTextEnd || '',
          }))
      })
    }

    window.NavicAndroidBridge.postMessage = message => {
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const snapshot = await Promise.resolve(readerBridgeDispatch({
        type: 'diagnosticLocationSnapshot',
        reason: 'whispersync-char-offset-overlay-initial',
      }))
      const visibleRange = latestPayload('visibleTextRange') ||
        snapshot?.visibleTextRangeResult?.visibleRange ||
        null
      if (!visibleRange) {
        throw new Error(`Expected visibleTextRange before char-offset probe; snapshot=${JSON.stringify(snapshot)}`)
      }
      const visibleStart = Number(visibleRange.visibleStart)
      const visibleEnd = Number(visibleRange.visibleEnd)
      if (!Number.isFinite(visibleStart) || !Number.isFinite(visibleEnd) || visibleEnd - visibleStart < 4) {
        throw new Error(`Visible range too small for char-offset probe: ${JSON.stringify(visibleRange)}`)
      }

      const targetStart = Math.floor(visibleStart + Math.max(1, Math.min(24, (visibleEnd - visibleStart) * 0.15)))
      const targetEnd = Math.min(Math.ceil(targetStart + 48), Math.floor(visibleEnd))
      if (targetEnd <= targetStart) {
        throw new Error(`Computed invalid char-offset range ${targetStart}-${targetEnd} from ${JSON.stringify(visibleRange)}`)
      }

      const overlayStartIndex = observedPayloads.length
      readerBridgeDispatch({
        type: 'applyOverlayFragment',
        fragment: {
          textHref: visibleRange.textHref,
          textStart: targetStart,
          textEnd: targetEnd,
          resourceHref: 'navic-whispersync-char-offset-overlay-probe',
          clipBeginSeconds: 1,
          clipEndSeconds: 2,
          label: 'Whispersync character offset overlay probe',
        },
      })
      await settleFrames()

      const markerMatches = rangeMarkers()
      if (!markerMatches.length) {
        throw new Error(
          `Expected data-navic-media-overlay-range marker for ${targetStart}-${targetEnd}; observed=${
            JSON.stringify(observedPayloads.slice(overlayStartIndex))
          }`
        )
      }
      const marker = markerMatches.find(entry =>
        entry.textStart === String(targetStart) &&
        entry.textEnd === String(targetEnd) &&
        entry.className.includes('navic-active-overlay-fragment')
      )
      if (!marker) {
        throw new Error(`Expected navic-active-overlay-fragment marker with exact offsets; markers=${JSON.stringify(markerMatches)}`)
      }
      const overlayEvent = latestPayload('overlayFragmentActive', overlayStartIndex)

      await Promise.resolve(readerBridgeDispatch({ type: 'clearOverlay' }))
      await settleFrames()
      const afterClearMarkers = rangeMarkers()
      if (afterClearMarkers.length) {
        throw new Error(`clearOverlay left char-offset markers behind: ${JSON.stringify(afterClearMarkers)}`)
      }

      return {
        probe: 'whispersync-char-offset-overlay',
        visibleRange,
        targetRange: {
          textHref: visibleRange.textHref,
          textStart: targetStart,
          textEnd: targetEnd,
        },
        marker,
        overlayEvent,
        observedOverlayFragments: observedPayloads.filter(payload => payload?.type === 'overlayFragmentActive'),
        expectedLogLabels: [
          'Reader bridge event: visibleTextRange',
          'overlayFragmentActive',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
    }
  }})()`)
}

async function runChapterProgressEndpointsProbe(page) {
  return evaluateOnPage(page, `window.__navicChapterProgressProbePromise = (${async () => {
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const observedPayloads = []
    const animationSettled = () => new Promise(resolve => {
      requestAnimationFrame(() => requestAnimationFrame(resolve))
    })
    const latestLocation = startIndex => {
      for (let index = observedPayloads.length - 1; index >= startIndex; index -= 1) {
        const payload = observedPayloads[index]
        if (payload?.type === 'locationChanged') return payload
      }
      return null
    }
    const numeric = value => {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : null
    }

    window.NavicAndroidBridge.postMessage = message => {
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const snapshot = async reason => {
        const startIndex = observedPayloads.length
        const result = await Promise.resolve(readerBridgeDispatch({
          type: 'diagnosticLocationSnapshot',
          reason,
        }))
        const returnedLocation = result?.message?.type === 'locationChanged'
          ? result.message
          : null
        return {
          result,
          location: latestLocation(startIndex) || returnedLocation,
        }
      }
      const endpoint = async (href, progress) => {
        const startIndex = observedPayloads.length
        const commandResult = await Promise.resolve(readerBridgeDispatch({
          type: 'goToChapterProgress',
          href,
          progress,
        }))
        await animationSettled()
        const locationSnapshot = await snapshot(`chapter-progress-endpoint-${progress}`)
        const location = locationSnapshot.location || latestLocation(startIndex)
        if (!location) {
          throw new Error(`Expected chapter-progress endpoint ${progress} to emit locationChanged`)
        }
        return {
          progress,
          commandResult,
          locationSnapshot,
          location,
        }
      }

      const initialSnapshot = await snapshot('chapter-progress-endpoints-initial')
      const initialLocation = initialSnapshot.location
      if (!initialLocation) {
        throw new Error('Expected chapter-progress initial snapshot to emit locationChanged')
      }
      const candidateHrefs = []
      const appendHref = href => {
        const normalized = String(href || '').trim()
        if (normalized && !candidateHrefs.includes(normalized)) candidateHrefs.push(normalized)
      }
      appendHref(initialLocation.href)
      const sections = Array.from(view?.book?.sections || [])
      for (const section of sections) {
        appendHref(section?.href || section?.id)
      }
      if (!candidateHrefs.length) {
        throw new Error('Expected chapter-progress probe to find at least one chapter href')
      }
      const candidateAttempts = []
      const successfulCandidates = []
      let bestCandidate = null
      for (const href of candidateHrefs) {
        let startProbe = null
        try {
          startProbe = await endpoint(href, 0)
        } catch (error) {
          candidateAttempts.push({ href, error: error?.message || String(error) })
          continue
        }
        const chapterPageCount = numeric(startProbe.location.chapterPageCount)
        candidateAttempts.push({
          href,
          chapterPageIndex: startProbe.location.chapterPageIndex,
          chapterPageCount: startProbe.location.chapterPageCount,
        })
        if (chapterPageCount != null) {
          successfulCandidates.push({
            href,
            chapterPageIndex: startProbe.location.chapterPageIndex,
            chapterPageCount: startProbe.location.chapterPageCount,
          })
        }
        if (chapterPageCount != null && chapterPageCount >= 2 && (!bestCandidate || chapterPageCount > bestCandidate.chapterPageCount)) {
          bestCandidate = {
            href,
            startProbe,
            chapterPageCount,
          }
        }
      }
      if (!bestCandidate) {
        throw new Error(
          `Expected chapter-progress-candidate with at least 2 pages; attempts=${JSON.stringify(candidateAttempts)}`
        )
      }

      const href = bestCandidate.href
      const start = bestCandidate.startProbe
      const end = await endpoint(href, 1)
      const startIndex = numeric(start.location.chapterPageIndex)
      if (startIndex !== 0) {
        throw new Error(`Expected chapter-progress endpoint 0 to report chapterPageIndex 0, got ${start.location.chapterPageIndex}`)
      }
      const endIndex = numeric(end.location.chapterPageIndex)
      const endCount = numeric(end.location.chapterPageCount)
      if (endCount == null || endCount < 2) {
        throw new Error(`Expected chapter-progress endpoint 1 to report a usable chapterPageCount, got ${end.location.chapterPageCount}`)
      }
      if (endIndex !== endCount - 1) {
        throw new Error(
          `Expected chapter-progress endpoint 1 to report last index ${endCount - 1}, got ${end.location.chapterPageIndex}`
        )
      }

      return {
        probe: 'chapter-progress-endpoints',
        href,
        initialLocation,
        candidateAttempts,
        successfulCandidates,
        bestCandidate: {
          href: bestCandidate.href,
          chapterPageCount: bestCandidate.chapterPageCount,
        },
        endpoints: [start, end],
        observedLocationCount: observedPayloads.filter(payload => payload?.type === 'locationChanged').length,
        expectedLogLabels: [
          'Reader bridge event: locationChanged',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
      window.__navicChapterProgressProbePromise = null
    }
  }})(); window.__navicChapterProgressProbePromise`)
}

async function runCurrentChapterProgressEndpointsProbe(page) {
  return evaluateOnPage(page, `window.__navicCurrentChapterProgressProbePromise = (${async () => {
    const readerBridgeDispatch = window.NavicReaderBridge?.dispatch?.bind(window.NavicReaderBridge)
    if (!readerBridgeDispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const originalPostMessage = window.NavicAndroidBridge?.postMessage?.bind(window.NavicAndroidBridge)
    if (!originalPostMessage) {
      throw new Error('Missing NavicAndroidBridge.postMessage')
    }
    const observedPayloads = []
    const latestLocation = startIndex => {
      for (let index = observedPayloads.length - 1; index >= startIndex; index -= 1) {
        const payload = observedPayloads[index]
        if (payload?.type === 'locationChanged') return payload
      }
      return null
    }
    const numeric = value => {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : null
    }

    window.NavicAndroidBridge.postMessage = message => {
      originalPostMessage(message)
      try {
        observedPayloads.push(JSON.parse(message))
      } catch {
        // Keep forwarding malformed messages to Android; they are not this probe's target.
      }
    }

    try {
      const snapshot = async reason => {
        const startIndex = observedPayloads.length
        const result = await Promise.resolve(readerBridgeDispatch({
          type: 'diagnosticLocationSnapshot',
          reason,
        }))
        const returnedLocation = result?.message?.type === 'locationChanged'
          ? result.message
          : null
        return {
          result,
          location: latestLocation(startIndex) || returnedLocation,
        }
      }
      const endpoint = async (href, progress) => {
        const startIndex = observedPayloads.length
        const commandResult = await Promise.resolve(readerBridgeDispatch({
          type: 'goToChapterProgress',
          href,
          progress,
        }))
        const locationSnapshot = await snapshot(`chapter-progress-current-endpoint-${progress}`)
        const location = locationSnapshot.location || latestLocation(startIndex)
        if (!location) {
          throw new Error(`Expected current chapter-progress endpoint ${progress} to emit locationChanged`)
        }
        return {
          progress,
          commandResult,
          locationSnapshot,
          location,
        }
      }

      const initialSnapshot = await snapshot('chapter-progress-current-endpoints-initial')
      const initialLocation = initialSnapshot.location
      const href = initialLocation ? String(initialLocation.href || '').trim() : ''
      if (!href) {
        throw new Error(`Expected current chapter-progress probe to find a current href; location=${JSON.stringify(initialLocation)}`)
      }

      const start = await endpoint(href, 0)
      const end = await endpoint(href, 1)
      const startIndex = numeric(start.location.chapterPageIndex)
      if (startIndex !== 0) {
        throw new Error(`Expected current chapter-progress endpoint 0 to report chapterPageIndex 0, got ${start.location.chapterPageIndex}`)
      }
      const endIndex = numeric(end.location.chapterPageIndex)
      const endCount = numeric(end.location.chapterPageCount)
      if (endCount == null || endCount < 2) {
        throw new Error(`Expected current chapter-progress endpoint 1 to report a usable chapterPageCount, got ${end.location.chapterPageCount}`)
      }
      if (endIndex !== endCount - 1) {
        throw new Error(
          `Expected current chapter-progress endpoint 1 to report last index ${endCount - 1}, got ${end.location.chapterPageIndex}`
        )
      }

      return {
        probe: 'chapter-progress-current-endpoints',
        href,
        initialLocation,
        endpoints: [start, end],
        observedLocationCount: observedPayloads.filter(payload => payload?.type === 'locationChanged').length,
        expectedLogLabels: [
          'Reader bridge event: locationChanged',
        ],
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      window.NavicAndroidBridge.postMessage = originalPostMessage
      window.__navicCurrentChapterProgressProbePromise = null
    }
  }})(); window.__navicCurrentChapterProgressProbePromise`)
}

async function runPageBoxProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const roundRect = rect => ({
      x: Math.round(rect.x),
      y: Math.round(rect.y),
      width: Math.round(rect.width),
      height: Math.round(rect.height),
      top: Math.round(rect.top),
      right: Math.round(rect.right),
      bottom: Math.round(rect.bottom),
      left: Math.round(rect.left),
    })
    const view = document.querySelector('foliate-view')
    const renderer = view?.renderer || document.querySelector('foliate-paginator')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    if (!renderer) {
      throw new Error('Missing Foliate renderer')
    }
    const rendererStyle = getComputedStyle(renderer)
    const contentEntries = Array.from(renderer.getContents?.() || [])
    const ratio = (part, whole) => {
      const numerator = Number(part)
      const denominator = Number(whole)
      return Number.isFinite(numerator) && Number.isFinite(denominator) && denominator > 0
        ? Number((numerator / denominator).toFixed(3))
        : null
    }
    const contentRects = contentEntries.map((entry, index) => {
      const doc = entry?.doc || entry?.document || entry?.iframe?.contentDocument || null
      const contentDocument = Boolean(doc)
      const body = doc?.body || null
      const documentElement = doc?.documentElement || null
      const bodyRect = body?.getBoundingClientRect?.()
      const elementRect = documentElement?.getBoundingClientRect?.()
      const visibleElement = element => {
        const rect = element?.getBoundingClientRect?.()
        if (!rect || rect.width <= 0 || rect.height <= 0) return false
        const style = doc.defaultView.getComputedStyle(element)
        return style.display !== 'none' && style.visibility !== 'hidden'
      }
      const firstVisibleElement = Array.from(body?.querySelectorAll?.('*') || [])
        .slice(0, 160)
        .find(visibleElement)
      const firstVisibleRect = firstVisibleElement?.getBoundingClientRect?.()
      const firstVisibleStyle = firstVisibleElement ? doc.defaultView.getComputedStyle(firstVisibleElement) : null
      const firstHeadingElement = Array.from(body?.querySelectorAll?.('h1, h2, h3, h4, h5, h6, [role="heading"], *[epub\\:type]') || [])
        .slice(0, 24)
        .find(element => {
          if (/^H[1-6]$/i.test(element.tagName || '')) return visibleElement(element)
          if (element.getAttribute?.('role') === 'heading') return visibleElement(element)
          const epubType = String(element.getAttribute?.('epub:type') || '')
          return epubType.split(/\s+/).includes('title') && visibleElement(element)
        })
      const firstHeadingRect = firstHeadingElement?.getBoundingClientRect?.()
      const firstHeadingStyle = firstHeadingElement ? doc.defaultView.getComputedStyle(firstHeadingElement) : null
      const firstProseElement = Array.from(body?.querySelectorAll?.('p, li, blockquote, dd, div, span, font') || [])
        .find(element => {
          const text = String(element.textContent || '').replace(/\s+/g, ' ').trim()
          if (text.length < 24) return false
          return visibleElement(element)
        })
      const firstProseRect = firstProseElement?.getBoundingClientRect?.()
      const firstProseStyle = firstProseElement ? doc.defaultView.getComputedStyle(firstProseElement) : null
      return {
        index,
        contentDocument,
        href: entry?.section?.href || entry?.href || doc?.location?.href || '',
        bodyTextLength: String(body?.textContent || '').trim().length,
        bodyRect: bodyRect ? roundRect(bodyRect) : null,
        documentElementRect: elementRect ? roundRect(elementRect) : null,
        documentToViewportWidthRatio: elementRect ? ratio(elementRect.width, window.visualViewport?.width || window.innerWidth || 0) : null,
        bodyToDocumentWidthRatio: bodyRect && elementRect ? ratio(bodyRect.width, elementRect.width) : null,
        firstProse: firstProseElement ? {
          tagName: firstProseElement.tagName,
          textLength: String(firstProseElement.textContent || '').replace(/\s+/g, ' ').trim().length,
          rect: firstProseRect ? roundRect(firstProseRect) : null,
          fontSize: firstProseStyle?.fontSize || '',
          lineHeight: firstProseStyle?.lineHeight || '',
          maxWidth: firstProseStyle?.maxWidth || '',
          marginInlineStart: firstProseStyle?.marginInlineStart || '',
          marginInlineEnd: firstProseStyle?.marginInlineEnd || '',
          display: firstProseStyle?.display || '',
        } : null,
        chapterOpening: {
          capped: firstHeadingElement?.getAttribute?.('data-navic-chapter-opening-margin-capped') === 'true',
          firstVisible: firstVisibleElement ? {
            tagName: firstVisibleElement.tagName,
            id: firstVisibleElement.id || '',
            className: String(firstVisibleElement.className || ''),
            text: String(firstVisibleElement.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 96),
            rect: firstVisibleRect ? roundRect(firstVisibleRect) : null,
            marginBlockStart: firstVisibleStyle?.marginBlockStart || '',
            marginTop: firstVisibleStyle?.marginTop || '',
            paddingBlockStart: firstVisibleStyle?.paddingBlockStart || '',
            paddingTop: firstVisibleStyle?.paddingTop || '',
          } : null,
          firstHeading: firstHeadingElement ? {
            tagName: firstHeadingElement.tagName,
            id: firstHeadingElement.id || '',
            className: String(firstHeadingElement.className || ''),
            text: String(firstHeadingElement.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 96),
            rect: firstHeadingRect ? roundRect(firstHeadingRect) : null,
            marginBlockStart: firstHeadingStyle?.marginBlockStart || '',
            marginTop: firstHeadingStyle?.marginTop || '',
            originalMargin: firstHeadingElement.dataset?.navicChapterOpeningOriginalMargin || '',
            cap: firstHeadingElement.dataset?.navicChapterOpeningMarginCap || '',
          } : null,
        },
      }
    })
    return {
      probe: 'page-box',
      pageTitle: document.title,
      pageUrl: window.location.href,
      viewport: {
        width: Math.round(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0),
        height: Math.round(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0),
        innerWidth: Math.round(window.innerWidth || 0),
        innerHeight: Math.round(window.innerHeight || 0),
        devicePixelRatio: window.devicePixelRatio || 1,
      },
      viewRect: roundRect(view.getBoundingClientRect()),
      rendererRect: roundRect(renderer.getBoundingClientRect()),
      rendererAttributes: {
        flow: renderer.getAttribute('flow') || '',
        maxInlineSize: renderer.getAttribute('max-inline-size') || '',
        maxBlockSize: renderer.getAttribute('max-block-size') || '',
        maxColumnCount: renderer.getAttribute('max-column-count') || '',
        columnThreshold: renderer.getAttribute('column-threshold') || '',
        topMargin: renderer.getAttribute('top-margin') || '',
        bottomMargin: renderer.getAttribute('bottom-margin') || '',
      },
      rendererComputed: {
        display: rendererStyle.display,
        width: rendererStyle.width,
        height: rendererStyle.height,
      },
      closedShadowRoot: renderer.shadowRoot === null,
      contentRects,
    }
  }})()`)
}

async function runVisiblePageContentProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const roundRect = rect => ({
      x: Math.round(rect.x),
      y: Math.round(rect.y),
      width: Math.round(rect.width),
      height: Math.round(rect.height),
      top: Math.round(rect.top),
      right: Math.round(rect.right),
      bottom: Math.round(rect.bottom),
      left: Math.round(rect.left),
    })
    const clamp = (value, min, max) => Math.min(max, Math.max(min, value))
    const viewport = {
      width: Math.round(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0),
      height: Math.round(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0),
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    const renderer = view.renderer
    if (!renderer) {
      throw new Error('Missing foliate renderer')
    }
    const rendererContainerPosition = Number(renderer.containerPosition)
    const horizontalOffset = Number.isFinite(rendererContainerPosition) ? rendererContainerPosition : 0
    const visibleContentItems = []
    for (const [contentIndex, entry] of Array.from(renderer.getContents?.() || []).entries()) {
      const doc = entry?.doc
      const win = doc?.defaultView
      if (!doc?.body || !win) continue
      for (const element of Array.from(doc.body.querySelectorAll('body *'))) {
        const text = String(element.textContent || '').replace(/\s+/g, ' ').trim()
        if (!text) continue
        const rect = element.getBoundingClientRect?.()
        if (!rect || rect.width <= 0 || rect.height <= 0) continue
        const adjustedRect = {
          x: rect.x - horizontalOffset,
          y: rect.y,
          width: rect.width,
          height: rect.height,
          top: rect.top,
          right: rect.right - horizontalOffset,
          bottom: rect.bottom,
          left: rect.left - horizontalOffset,
        }
        const intersectionWidth = clamp(Math.min(adjustedRect.right, viewport.width) - Math.max(adjustedRect.left, 0), 0, viewport.width)
        const intersectionHeight = clamp(Math.min(adjustedRect.bottom, viewport.height) - Math.max(adjustedRect.top, 0), 0, viewport.height)
        if (intersectionWidth <= 0 || intersectionHeight <= 0) continue
        const style = win.getComputedStyle(element)
        if (style.display === 'none' || style.visibility === 'hidden') continue
        visibleContentItems.push({
          contentIndex,
          tagName: element.tagName,
          id: element.id || '',
          className: String(element.className || ''),
          textLength: text.length,
          textSample: text.slice(0, 160),
          rect: roundRect(rect),
          adjustedRect: roundRect(adjustedRect),
          viewportIntersectionRatio: Number(((intersectionWidth * intersectionHeight) / Math.max(1, rect.width * rect.height)).toFixed(4)),
          fontSize: style.fontSize,
          lineHeight: style.lineHeight,
        })
      }
    }
    const visibleTextLength = visibleContentItems.reduce((sum, item) => sum + item.textLength, 0)
    return {
      probe: 'visible-page-content',
      pageTitle: document.title,
      pageUrl: window.location.href,
      viewport,
      rendererPage: Number(renderer.page),
      rendererPages: Number(renderer.pages),
      rendererStart: Number(renderer.start),
      rendererEnd: Number(renderer.end),
      rendererViewSize: Number(renderer.viewSize),
      rendererContainerPosition,
      visibleTextLength,
      visibleElementCount: visibleContentItems.length,
      visibleItems: visibleContentItems.slice(0, 32),
    }
  }})()`)
}

async function runFontSizeProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    if (!window.NavicReaderBridge?.dispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    const content = view.renderer?.getContents?.()?.find?.(entry => entry?.doc?.body)
    const doc = content?.doc
    if (!doc?.body) {
      throw new Error('Missing loaded content document')
    }
    const win = doc.defaultView
    doc.querySelectorAll('[data-navic-font-size-probe="true"], [data-navic-publisher-font-size-probe="true"]')
      .forEach(element => element.remove())
    const existingTextElements = Array.from(doc.body.querySelectorAll('p, span, font, div, li, blockquote'))
      .filter(element => !element.closest('[data-navic-font-size-probe="true"], [data-navic-publisher-font-size-probe="true"]'))
      .filter(element => {
        const text = String(element.textContent || '').replace(/\s+/g, ' ').trim()
        if (text.length < 24) return false
        const rect = element.getBoundingClientRect()
        if (!rect || rect.width <= 0 || rect.height <= 0) return false
        const style = win.getComputedStyle(element)
        return style.display !== 'none' && style.visibility !== 'hidden'
      })
      .slice(0, 12)
    const describeElement = (element, index) => {
      const style = win.getComputedStyle(element)
      const text = String(element.textContent || '').replace(/\s+/g, ' ').trim()
      const rect = element.getBoundingClientRect()
      return {
        index,
        tagName: element.tagName,
        id: element.id || '',
        className: String(element.className || ''),
        fontSize: style.fontSize,
        fontSizeValue: Number.parseFloat(style.fontSize || '0'),
        lineHeight: style.lineHeight,
        rect: {
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        },
        text: text.slice(0, 96),
      }
    }
    const readExistingMetrics = label => ({
      label,
      elements: existingTextElements.map(describeElement),
    })
    const originalPercentText = win.getComputedStyle(doc.documentElement)
      .getPropertyValue('--reader-content-font-size')
      .trim()
    const originalPercent = Number.parseFloat(originalPercentText || '100')
    const probe = doc.createElement('section')
    probe.setAttribute('data-navic-font-size-probe', 'true')
    probe.innerHTML = '<p data-navic-font-size-probe-paragraph="true">Navic font-size probe paragraph text.</p>'
    doc.body.prepend(probe)
    const flushStyle = () => {
      void doc.documentElement.offsetHeight
    }
    try {
      flushStyle()
      const paragraph = doc.querySelector('[data-navic-font-size-probe-paragraph="true"]')
      const readMetrics = label => {
        const htmlStyle = win.getComputedStyle(doc.documentElement)
        const bodyStyle = win.getComputedStyle(doc.body)
        const paragraphStyle = win.getComputedStyle(paragraph)
        return {
          label,
          rootFontSize: htmlStyle.fontSize,
          bodyFontSize: bodyStyle.fontSize,
          paragraphFontSize: paragraphStyle.fontSize,
          rootFontSizeValue: Number.parseFloat(htmlStyle.fontSize || '0'),
          bodyFontSizeValue: Number.parseFloat(bodyStyle.fontSize || '0'),
          paragraphFontSizeValue: Number.parseFloat(paragraphStyle.fontSize || '0'),
          contentFontSizeVariable: htmlStyle.getPropertyValue('--reader-content-font-size').trim(),
        }
      }
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: { fontSizePercent: 100 },
      })
      flushStyle()
      const at100 = readMetrics('100')
      const existingAt100 = readExistingMetrics('100')
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: { fontSizePercent: 140 },
      })
      flushStyle()
      const at140 = readMetrics('140')
      const existingAt140 = readExistingMetrics('140')
      const existingDeltas = existingAt100.elements.map((before, index) => {
        const after = existingAt140.elements[index]
        return {
          index,
          tagName: before.tagName,
          text: before.text,
          before: before.fontSize,
          after: after?.fontSize || '',
          delta: Number(after?.fontSizeValue) - Number(before.fontSizeValue),
        }
      })
      const existingProseDeltas = existingDeltas.filter(item =>
        Number.isFinite(item.delta) &&
        (item.tagName === 'P' || item.tagName === 'BLOCKQUOTE' || item.tagName === 'LI' || item.tagName === 'DIV')
      )
      const existingProseDelta = existingProseDeltas.length
        ? Math.min(...existingProseDeltas.map(item => item.delta))
        : 0
      if (existingProseDelta < 5) {
        throw new Error(
          'Existing prose text did not scale with reader Font size: ' +
          JSON.stringify({ at100, at140, existingDeltas, existingProseDelta })
        )
      }
      return {
        probe: 'font-size',
        restoredFontSizePercent: Number.isFinite(originalPercent) ? originalPercent : 140,
        at100,
        at140,
        existingAt100,
        existingAt140,
        existingDeltas,
        existingProseDeltas,
        existingProseDelta,
        paragraphDelta: at140.paragraphFontSizeValue - at100.paragraphFontSizeValue,
        bodyDelta: at140.bodyFontSizeValue - at100.bodyFontSizeValue,
        rootDelta: at140.rootFontSizeValue - at100.rootFontSizeValue,
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      probe.remove()
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: { fontSizePercent: Number.isFinite(originalPercent) ? originalPercent : 140 },
      })
    }
  }})()`)
}

async function runPublisherStyleFontSizeProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    if (!window.NavicReaderBridge?.dispatch) {
      throw new Error('Missing NavicReaderBridge.dispatch')
    }
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    const content = view.renderer?.getContents?.()?.find?.(entry => entry?.doc?.body)
    const doc = content?.doc
    if (!doc?.body) {
      throw new Error('Missing loaded content document')
    }
    const win = doc.defaultView
    doc.querySelectorAll('[data-navic-font-size-probe="true"], [data-navic-publisher-font-size-probe="true"]')
      .forEach(element => element.remove())
    const originalPercentText = win.getComputedStyle(doc.documentElement)
      .getPropertyValue('--reader-content-font-size')
      .trim()
    const originalPercent = Number.parseFloat(originalPercentText || '100')
    const originalStyleText = doc.getElementById('navic-reader-document-theme')?.textContent || ''
    const originalPublisherStyles = !originalStyleText.includes('font-weight:') &&
      !originalStyleText.includes('letter-spacing:')
    const publisherStyle = doc.createElement('style')
    publisherStyle.setAttribute('data-navic-publisher-font-size-probe-style', 'true')
    publisherStyle.textContent = `
      [data-navic-publisher-font-size-probe="true"] .publisher-important-wrapper p,
      [data-navic-publisher-font-size-probe="true"] .publisher-important-wrapper span {
        font-size: 10px !important;
      }
    `
    const probe = doc.createElement('section')
    probe.setAttribute('data-navic-publisher-font-size-probe', 'true')
    probe.innerHTML = `
      <p data-navic-publisher-font-size-probe-paragraph="true" style="font-size: 12px">Navic publisher style font-size probe paragraph text.</p>
      <section class="publisher-important-wrapper">
        <p data-navic-publisher-font-size-probe-class-important="true">
          <span>Navic publisher class-important font-size probe paragraph text.</span>
        </p>
      </section>
    `
    doc.head.append(publisherStyle)
    doc.body.prepend(probe)
    const flushStyle = () => {
      void doc.documentElement.offsetHeight
    }
    try {
      flushStyle()
      const paragraph = doc.querySelector('[data-navic-publisher-font-size-probe-paragraph="true"]')
      const readMetrics = label => {
        const htmlStyle = win.getComputedStyle(doc.documentElement)
        const paragraphStyle = win.getComputedStyle(paragraph)
        const classImportantStyle = win.getComputedStyle(
          doc.querySelector('[data-navic-publisher-font-size-probe-class-important="true"] span')
        )
        return {
          label,
          rootFontSize: htmlStyle.fontSize,
          rootFontSizeValue: Number.parseFloat(htmlStyle.fontSize || '0'),
          publisherParagraphFontSize: paragraphStyle.fontSize,
          publisherParagraphFontSizeValue: Number.parseFloat(paragraphStyle.fontSize || '0'),
          publisherClassImportantFontSize: classImportantStyle.fontSize,
          publisherClassImportantFontSizeValue: Number.parseFloat(classImportantStyle.fontSize || '0'),
          contentFontSizeVariable: htmlStyle.getPropertyValue('--reader-content-font-size').trim(),
        }
      }
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: {
          publisherStyles: true,
          fontSizePercent: 100,
        },
      })
      flushStyle()
      const at100 = readMetrics('100')
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: {
          publisherStyles: true,
          fontSizePercent: 140,
        },
      })
      flushStyle()
      const at140 = readMetrics('140')
      const publisherParagraphDelta = at140.publisherParagraphFontSizeValue - at100.publisherParagraphFontSizeValue
      const publisherClassImportantDelta =
        at140.publisherClassImportantFontSizeValue - at100.publisherClassImportantFontSizeValue
      const rootDelta = at140.rootFontSizeValue - at100.rootFontSizeValue
      if (publisherParagraphDelta < 5) {
        throw new Error(
          'Publisher-style fixed paragraph did not scale with reader Font size: ' +
          JSON.stringify({ at100, at140, publisherParagraphDelta, rootDelta })
        )
      }
      if (publisherClassImportantDelta < 5) {
        throw new Error(
          'Publisher-style class-important paragraph did not scale with reader Font size: ' +
          JSON.stringify({ at100, at140, publisherClassImportantDelta, rootDelta })
        )
      }
      return {
        probe: 'font-size-publisher-styles',
        restoredFontSizePercent: Number.isFinite(originalPercent) ? originalPercent : 140,
        restoredPublisherStyles: originalPublisherStyles,
        at100,
        at140,
        publisherParagraphDelta,
        publisherClassImportantDelta,
        rootDelta,
        pageTitle: document.title,
        pageUrl: window.location.href,
      }
    } finally {
      probe.remove()
      publisherStyle.remove()
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: {
          fontSizePercent: Number.isFinite(originalPercent) ? originalPercent : 140,
          publisherStyles: originalPublisherStyles,
        },
      })
    }
  }})()`)
}

async function runRuntimeStateProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    const renderer = view?.renderer
    const contents = renderer?.getContents?.()?.filter?.(entry => entry?.doc?.body) || []
    const rendererElement = renderer?.element || renderer
    return {
      probe: 'runtime-state',
      flowMode: document.body.dataset.navicReaderFlowMode || '',
      rendererFlow: view?.getAttribute?.('flow') || rendererElement?.getAttribute?.('flow') || '',
      scrolled: renderer?.scrolled === true,
      start: Number.isFinite(Number(renderer?.start)) ? Number(renderer.start) : null,
      end: Number.isFinite(Number(renderer?.end)) ? Number(renderer.end) : null,
      viewSize: Number.isFinite(Number(renderer?.viewSize)) ? Number(renderer.viewSize) : null,
      contentCount: contents.length,
      contentGesturesAttached: contents.filter(entry => entry.doc?.defaultView?.__navicScrolledEdgeTurnGesturesAttached).length,
      selectionActive: contents.some(entry => {
        const selection = entry.doc?.getSelection?.()
        return selection && selection.rangeCount > 0 && !selection.isCollapsed
      }),
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
      },
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function runImageHitTargetsProbe(page) {
  return evaluateOnPage(page, `(${async () => {
    const view = document.querySelector('foliate-view')
    if (!view) {
      throw new Error('Missing foliate-view')
    }
    const contents = view.renderer?.getContents?.()?.filter?.(entry => entry?.doc?.body) || []
    const targets = []
    for (const entry of contents) {
      const doc = entry.doc
      const frameRect = doc.defaultView?.frameElement?.getBoundingClientRect?.()
      const media = Array.from(doc.querySelectorAll('img, svg, image, video, audio, picture, object, canvas'))
      for (const element of media) {
        const rect = element.getBoundingClientRect?.()
        if (!rect || rect.width <= 1 || rect.height <= 1) continue
        const rootLeft = (frameRect?.left || 0) + rect.left
        const rootTop = (frameRect?.top || 0) + rect.top
        const rootRight = rootLeft + rect.width
        const rootBottom = rootTop + rect.height
        const visible = rootRight > 0 &&
          rootBottom > 0 &&
          rootLeft < window.innerWidth &&
          rootTop < window.innerHeight
        targets.push({
          index: Number.isFinite(entry.index) ? entry.index : null,
          href: entry.section?.href || '',
          tagName: element.tagName,
          src: element.currentSrc || element.getAttribute?.('src') || element.getAttribute?.('href') || '',
          alt: element.getAttribute?.('alt') || '',
          rootX: Math.round(rootLeft + rect.width / 2),
          rootY: Math.round(rootTop + rect.height / 2),
          rootLeft: Math.round(rootLeft),
          rootTop: Math.round(rootTop),
          width: Math.round(rect.width),
          height: Math.round(rect.height),
          visible,
        })
      }
    }
    return {
      probe: 'image-hit-targets',
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
      },
      contentCount: contents.length,
      visibleTargets: targets.filter(target => target.visible),
      targets,
      pageTitle: document.title,
      pageUrl: window.location.href,
    }
  }})()`)
}

async function main() {
  const pidOutput = runAdb(['shell', 'pidof', packageName])
  const pid = pidOutput.split(/\s+/).find(Boolean)
  if (!pid) {
    throw new Error(`Package is not running: ${packageName}`)
  }

  const socketName = `webview_devtools_remote_${pid}`
  const portSpec = `tcp:${localPort}`
  runAdb(['forward', portSpec, `localabstract:${socketName}`])

  try {
    const page = await findReaderPage(localPort)
    const probeHandlers = {
      'internal-link-native': runInternalLinkNativeProbe,
      'phase3-events': runPhase3EventsProbe,
      'annotation-roundtrip': runAnnotationRoundTripProbe,
      'history-controls': runHistoryControlsProbe,
      'selection-payload': runSelectionPayloadProbe,
      'relocation-payload': runRelocationPayloadProbe,
      'visible-range': runVisibleRangeProbe,
      'whispersync-audio-follow': runWhispersyncAudioFollowProbe,
      'whispersync-page-scoped-control': runWhispersyncPageScopedControlProbe,
      'whispersync-char-offset-overlay': runWhispersyncCharOffsetOverlayProbe,
      'chapter-progress-endpoints': runChapterProgressEndpointsProbe,
      'chapter-progress-current-endpoints': runCurrentChapterProgressEndpointsProbe,
      'page-box': runPageBoxProbe,
      'visible-page-content': runVisiblePageContentProbe,
      'font-size': runFontSizeProbe,
      'font-size-publisher-styles': runPublisherStyleFontSizeProbe,
      'runtime-state': runRuntimeStateProbe,
      'image-hit-targets': runImageHitTargetsProbe,
    }
    const handler = probeHandlers[probe]
    if (!handler) {
      throw new Error(`Unsupported probe: ${probe}`)
    }
    const result = await handler(page)
    console.log(JSON.stringify({
      packageName,
      deviceSerial,
      pid,
      socketName,
      adbForwardCommand,
      probe,
      result,
    }, null, 2))
  } finally {
    spawnSync('adb', adbArgs(['forward', '--remove', portSpec]), { encoding: 'utf8' })
  }
}

main().catch(error => {
  console.error(error?.stack || error?.message || String(error))
  process.exit(1)
})
