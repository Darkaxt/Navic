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
        'Reader bridge event: selectionChanged',
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
      'selection-payload': runSelectionPayloadProbe,
      'relocation-payload': runRelocationPayloadProbe,
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
