import './vendor/foliate-js/view.js'

const readerRoot = document.body
const overlayClass = 'navic-active-overlay-fragment'
const ScrollEdgeTurnSwipeThreshold = 60
const ScrollEdgeTurnSlop = 2

const log = (label, ...details) => console.debug('[NavicReader]', label, ...details)
const logError = (label, ...details) => console.error('[NavicReader]', label, ...details)

const describeUrl = url => {
  try {
    const parsed = new URL(url)
    const fileName = parsed.pathname.split('/').filter(Boolean).pop() || ''
    return `${parsed.protocol}${fileName}`
  } catch {
    return typeof url === 'string' ? url.slice(0, 80) : typeof url
  }
}

const post = message => {
  const json = JSON.stringify(message)
  log('post', message.type, message.code || '')
  if (window.NavicAndroidBridge?.postMessage) {
    window.NavicAndroidBridge.postMessage(json)
  } else {
    log('bridge-unavailable', message)
  }
}

const reportError = (error, code = 'reader_error') => {
  const message = error?.message || String(error)
  logError('reportError', code, message, error?.stack || error)
  readerRoot.replaceChildren(errorElement(message))
  post({ type: 'error', code, message })
}

const errorElement = message => {
  const element = document.createElement('div')
  element.className = 'reader-error'
  element.textContent = message
  return element
}

const optionalNumber = value =>
  Number.isFinite(value) ? value : undefined

const setStylesImportant = (element, styles) => {
  if (!element) return
  for (const [property, value] of Object.entries(styles)) {
    element.style.setProperty(property, value, 'important')
  }
}

const readerViewportSize = () => {
  const viewport = window.visualViewport
  const width = Math.max(
    1,
    Math.round(viewport?.width || window.innerWidth || document.documentElement.clientWidth || 0)
  )
  const height = Math.max(
    1,
    Math.round(viewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
  )
  return { width, height }
}

class NavicReaderRuntime {
  view = null
  mediaOverlayEnabled = false
  viewportResizeListener = () => this.applyReaderViewportLayout('resize')

  constructor() {
    window.visualViewport?.addEventListener('resize', this.viewportResizeListener)
    window.addEventListener('resize', this.viewportResizeListener)
    requestAnimationFrame(() => this.applyReaderViewportLayout('startup'))
  }

  dispatch(command) {
    log('dispatch', command?.type || 'invalid')
    if (!command || typeof command !== 'object') return
    switch (command.type) {
      case 'openPublication':
        return this.openPublication(command)
      case 'goToCfi':
        return this.goTo(command.cfi)
      case 'goToHref':
        return this.goTo(command.href)
      case 'nextPage':
        return this.nextPage()
      case 'previousPage':
        return this.previousPage()
      case 'applyHighlight':
        return this.applyHighlight(command)
      case 'applyHighlights':
        return this.applyHighlights(command.highlights || [])
      case 'applyOverlayFragment':
        return this.applyOverlayFragment(command.fragment || command)
      case 'clearOverlay':
        return this.clearOverlay()
      case 'applySettings':
        return this.applySettings(command.settings || {})
      case 'search':
        return this.search(command.query)
      default:
        post({ type: 'error', code: 'unknown_command', message: `Unknown reader command: ${command.type}` })
    }
  }

  async openPublication({ url, mediaOverlayEnabled = false, startLocator = null, settings = null }) {
    if (!url) {
      logError('openPublication:missing-url')
      post({ type: 'error', code: 'missing_url', message: 'Reader publication URL is required.' })
      return
    }
    this.mediaOverlayEnabled = Boolean(mediaOverlayEnabled)
    log('openPublication:start', describeUrl(url), `overlay=${this.mediaOverlayEnabled}`)
    try {
      this.close()
      this.applyReaderViewportLayout('before-open')
      this.view = document.createElement('foliate-view')
      this.view.addEventListener('relocate', event => this.onRelocate(event.detail || {}))
      this.view.addEventListener('load', event => this.onLoad(event.detail || {}))
      this.view.addEventListener('external-link', event => event.preventDefault())
      readerRoot.replaceChildren(this.view)
      this.applyReaderViewportLayout('view-created')
      await this.view.open(url)
      this.applyReaderViewportLayout('view-opened')
      log('openPublication:view-opened', describeUrl(url))
      if (settings) this.applySettings(settings)
      this.postToc()
      const locator = startLocator?.cfi || startLocator?.href
      if (locator) {
        await this.view.goTo(locator)
      } else {
        await this.view.init?.({ showTextStart: true })
      }
      log('openPublication:ready', describeUrl(url))
      this.applyReaderViewportLayout('ready')
      this.logContentLayout('ready')
      post({ type: 'ready' })
      post({ type: 'publicationReady' })
    } catch (error) {
      reportError(error, 'open_failed')
    }
  }

  close() {
    this.clearOverlay()
    this.view?.close?.()
    this.view?.remove?.()
    this.view = null
  }

  applyReaderViewportLayout(label = 'unknown') {
    const { width, height } = readerViewportSize()
    const widthPx = `${width}px`
    const heightPx = `${height}px`
    setStylesImportant(document.documentElement, {
      width: '100%',
      height: heightPx,
      'min-height': heightPx,
      margin: '0px',
      overflow: 'hidden',
    })
    setStylesImportant(document.body, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      margin: '0px',
      overflow: 'hidden',
    })
    setStylesImportant(this.view, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: 'hidden',
    })
    const renderer = this.view?.renderer
    setStylesImportant(renderer, {
      position: 'absolute',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: 'hidden',
    })
    if (renderer) requestAnimationFrame(() => renderer?.render?.())
    log('viewport-layout', `label=${label}`, `${width}x${height}`)
  }

  async goTo(locator) {
    if (!this.view || !locator) return
    try {
      await this.view.goTo(locator)
      this.logContentLayout('go-to')
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  async nextPage() {
    return this.turnPage('next')
  }

  async previousPage() {
    return this.turnPage('previous')
  }

  async turnPage(direction) {
    if (!this.view) return
    try {
      log('page-turn:start', direction)
      if (direction === 'next') {
        await this.view?.next?.()
      } else {
        await this.view?.prev?.()
      }
      this.applyReaderViewportLayout(`page-turn:${direction}`)
      requestAnimationFrame(() => {
        this.logContentLayout(`page-turn:${direction}`)
        log('page-turn:done', direction)
      })
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  attachScrolledEdgeTurnGestures(doc) {
    if (!doc?.defaultView || doc.defaultView.__navicScrolledEdgeTurnGesturesAttached) return
    doc.defaultView.__navicScrolledEdgeTurnGesturesAttached = true
    let touchState = null
    doc.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
      }
    }, { passive: true })
    doc.addEventListener('touchmove', event => {
      if (!touchState || event.touches?.length > 1) {
        touchState = null
        return
      }
      const touch = event.changedTouches?.[0]
      if (!touch) return
      touchState.lastX = touch.screenX ?? touch.clientX ?? touchState.x
      touchState.lastY = touch.screenY ?? touch.clientY ?? touchState.y
    }, { passive: true })
    doc.addEventListener('touchend', event => {
      const state = touchState
      touchState = null
      if (!state) return
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const endX = touch.screenX ?? touch.clientX ?? state.lastX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.lastY ?? state.y
      const deltaX = endX - state.x
      const deltaY = endY - state.y
      const selection = doc.getSelection?.()
      if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return
      if (Math.abs(deltaY) < ScrollEdgeTurnSwipeThreshold || Math.abs(deltaY) <= Math.abs(deltaX)) return
      this.turnScrolledEdgePage(deltaY)
    }, { passive: true })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
  }

  turnScrolledEdgePage(deltaY) {
    const renderer = this.view?.renderer
    if (!renderer || !renderer.scrolled) return false
    const atStart = renderer.start <= ScrollEdgeTurnSlop
    const atEnd = renderer.viewSize - renderer.end <= ScrollEdgeTurnSlop
    if (deltaY > ScrollEdgeTurnSwipeThreshold && atStart) {
      log('page-turn:edge-swipe', 'previous', `start=${renderer.start}`)
      void this.previousPage()
      return true
    }
    if (deltaY < -ScrollEdgeTurnSwipeThreshold && atEnd) {
      log('page-turn:edge-swipe', 'next', `remaining=${renderer.viewSize - renderer.end}`)
      void this.nextPage()
      return true
    }
    return false
  }

  async applyHighlight({ id, cfi, color = null, note = null }) {
    if (!this.view || !id || !cfi) return
    try {
      await this.view.addAnnotation?.({ id, value: cfi, color, note }, false)
    } catch (error) {
      reportError(error, 'highlight_failed')
    }
  }

  async applyHighlights(highlights) {
    for (const highlight of highlights || []) {
      await this.applyHighlight(highlight)
    }
  }

  async applyOverlayFragment(fragment) {
    if (!this.view || !fragment) return
    const targetHref = fragment.textHref && fragment.fragmentId
      ? `${fragment.textHref}#${fragment.fragmentId}`
      : fragment.textHref
    if (targetHref) {
      await this.goTo(targetHref)
    }
    this.clearOverlay()
    if (fragment.fragmentId) {
      for (const doc of this.contentDocuments()) {
        const element = doc.getElementById(fragment.fragmentId)
        if (element) element.classList.add(overlayClass)
      }
    }
    post({ type: 'overlayFragmentActive', ...fragment })
  }

  clearOverlay() {
    for (const doc of this.contentDocuments()) {
      for (const element of doc.querySelectorAll(`.${overlayClass}`)) {
        element.classList.remove(overlayClass)
      }
    }
  }

  applySettings(settings) {
    const rootStyle = document.documentElement.style
    if (settings.fontSizePercent) rootStyle.setProperty('--reader-font-size', `${settings.fontSizePercent}%`)
    if (settings.lineHeight) rootStyle.setProperty('--reader-line-height', String(settings.lineHeight))
    if (settings.theme === 'dark') {
      rootStyle.setProperty('--reader-background', '#111315')
      rootStyle.setProperty('--reader-foreground', '#f2f0ea')
    } else if (settings.theme === 'light') {
      rootStyle.setProperty('--reader-background', '#fbfaf8')
      rootStyle.setProperty('--reader-foreground', '#1d1b18')
    }
    if (typeof settings.paged === 'boolean') {
      this.view?.renderer?.setAttribute('flow', settings.paged ? 'paginated' : 'scrolled')
    }
    this.applyReaderViewportLayout('settings')
    this.view?.renderer?.setStyles?.(readerContentCss(settings))
  }

  async search(query) {
    if (!this.view || !query) return
    try {
      const results = []
      for await (const result of this.view.search?.({ query }) || []) {
        results.push(...normalizeSearchResult(result, results.length, this.view))
      }
      post({ type: 'searchResults', query, results })
    } catch (error) {
      reportError(error, 'search_failed')
    }
  }

  postToc() {
    const items = flattenTocItems(this.view?.book?.toc || [])
    post({ type: 'toc', items })
  }

  onRelocate(detail) {
    const tocItem = detail.tocItem || {}
    post({
      type: 'locationChanged',
      href: detail.href || tocItem.href,
      cfi: detail.cfi,
      progress: optionalNumber(detail.fraction ?? detail.progress ?? detail.totalProgress),
      tocTitle: tocItem.label || tocItem.title,
    })
    if (detail.cfi) post({ type: 'cfiChanged', cfi: detail.cfi })
    if (tocItem.href || tocItem.label || tocItem.title) {
      post({ type: 'tocItemChanged', href: tocItem.href, title: tocItem.label || tocItem.title })
    }
  }

  onLoad() {
    this.applyReaderViewportLayout('load')
    const contents = this.view?.renderer?.getContents?.() || []
    for (const content of contents) {
      const doc = content.doc
      if (!doc) continue
      this.attachScrolledEdgeTurnGestures(doc)
      if (doc.defaultView?.__navicSelectionBridgeAttached) continue
      doc.defaultView.__navicSelectionBridgeAttached = true
      doc.addEventListener('selectionchange', () => {
        const selection = doc.getSelection()
        const text = selection?.toString?.().trim()
        if (!text) {
          post({ type: 'selectionChanged' })
          return
        }
        const range = selection.rangeCount ? selection.getRangeAt(0) : null
        const cfi = range && Number.isFinite(content.index)
          ? this.view?.getCFI?.(content.index, range)
          : undefined
        post({
          type: 'selectionChanged',
          text,
          cfi,
          href: this.view?.book?.sections?.[content.index]?.href,
        })
      })
    }
    requestAnimationFrame(() => {
      requestAnimationFrame(() => this.logContentLayout('load'))
    })
    if (this.mediaOverlayEnabled) post({ type: 'overlayFragmentInactive' })
  }

  logContentLayout(label = 'unknown') {
    const describeRect = rect => rect
      ? `${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      : 'missing'
    const contents = this.view?.renderer?.getContents?.() || []
    const surfaceRect = this.view?.getBoundingClientRect?.()
    const rendererRect = this.view?.renderer?.getBoundingClientRect?.()
    log(
      'surface-layout',
      `label=${label}`,
      `view=${describeRect(surfaceRect)}`,
      `renderer=${describeRect(rendererRect)}`,
      `inner=${Math.round(window.innerWidth)}x${Math.round(window.innerHeight)}`,
      `visual=${Math.round(window.visualViewport?.width || 0)}x${Math.round(window.visualViewport?.height || 0)}`
    )
    if (!contents.length) {
      log('content-layout', `label=${label}`, 'contents=0')
      return
    }
    for (const content of contents) {
      const doc = content.doc
      if (!doc) {
        log('content-layout', `label=${label}`, `index=${content.index}`, 'doc=missing')
        continue
      }
      const frameElement = doc.defaultView?.frameElement
      const frameRect = frameElement?.getBoundingClientRect?.()
      const bodyRect = doc.body?.getBoundingClientRect?.()
      const htmlRect = doc.documentElement?.getBoundingClientRect?.()
      const bodyStyle = doc.defaultView.getComputedStyle(doc.body)
      const htmlStyle = doc.defaultView.getComputedStyle(doc.documentElement)
      const textLength = doc.body?.textContent?.replace(/\s+/g, ' ').trim().length ?? 0
      log(
        'content-layout',
        `label=${label}`,
        `index=${content.index}`,
        `frameElement=${frameElement?.localName || 'missing'}`,
        `iframe=${describeRect(frameRect)}`,
        `html=${describeRect(htmlRect)}`,
        `body=${describeRect(bodyRect)}`,
        `textLength=${textLength}`,
        `color=${bodyStyle.color}`,
        `background=${bodyStyle.backgroundColor || htmlStyle.backgroundColor}`
      )
    }
  }

  contentDocuments() {
    return this.view?.renderer?.getContents?.().map(content => content.doc).filter(Boolean) || []
  }
}

const readerContentCss = settings => `
  html {
    color: var(--reader-foreground);
    background: var(--reader-background);
    font-size: ${settings.fontSizePercent || 100}%;
  }
  body {
    line-height: ${settings.lineHeight || 1.55};
    font-family: ${settings.fontFamily || 'system-ui, sans-serif'};
    margin-inline: ${settings.marginPercent || 0}%;
  }
  .${overlayClass} {
    background: color-mix(in srgb, var(--reader-accent) 28%, transparent);
    border-radius: 3px;
  }
`

const normalizeSearchResult = (result, startIndex, view) => {
  if (!result || result === 'done' || result.progress != null) return []
  const sectionTitle = result.label || result.sectionTitle
  const items = result.subitems || [result]
  return items
    .filter(item => item?.cfi || item?.href)
    .map((item, index) => {
      const excerpt = normalizeExcerpt(item.excerpt)
      const cfi = item.cfi
      return {
        id: item.id || cfi || item.href || `search-${startIndex + index}`,
        cfi,
        href: item.href || hrefForCfi(view, cfi),
        excerpt,
        sectionTitle,
      }
    })
}

const normalizeExcerpt = excerpt => {
  if (!excerpt) return undefined
  if (typeof excerpt === 'string') return excerpt
  return [excerpt.pre, excerpt.match, excerpt.post]
    .filter(Boolean)
    .join('')
    .replace(/\s+/g, ' ')
    .trim()
}

const hrefForCfi = (view, cfi) => {
  if (!view || !cfi) return undefined
  try {
    const parsed = view.resolveCFI?.(cfi)
    const section = view.book?.sections?.[parsed?.index]
    return section?.href
  } catch {
    return undefined
  }
}

const flattenTocItems = (items, level = 0, path = 'toc') =>
  (items || []).flatMap((item, index) => {
    const id = `${path}-${index}`
    const title = tocLabel(item)
    const href = item?.href
    const row = title || href
      ? [{ id: item?.id || href || id, title: title || href, href, level }]
      : []
    return [
      ...row,
      ...flattenTocItems(item?.subitems || [], level + 1, id),
    ]
  })

const tocLabel = item => {
  const label = item?.label || item?.title
  if (!label) return undefined
  if (typeof label === 'string') return label.trim() || undefined
  if (typeof label === 'object') {
    const value = Object.values(label).find(value => typeof value === 'string' && value.trim())
    return value?.trim()
  }
  return String(label).trim() || undefined
}

const runtime = new NavicReaderRuntime()

window.NavicReaderBridge = {
  dispatch: command => runtime.dispatch(command),
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: fragmentId => post({ type: 'overlayFragmentInactive', fragmentId }),
}

log('module-loaded')
post({ type: 'ready' })
