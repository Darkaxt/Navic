import './vendor/foliate-js/view.js'

const readerRoot = document.body
const overlayClass = 'navic-active-overlay-fragment'
const ScrollEdgeTurnSwipeThreshold = 60
const ScrollEdgeTurnSlop = 2
const ReaderTapZoneDefault = 'default'
const ReaderTapZoneEdge = 'edge'
const ReaderTapZoneKindle = 'kindle'
const ReaderTapZoneLShaped = 'l-shaped'
const ReaderTapZoneRightLeft = 'right-left'
const ReaderTapZoneDisabled = 'disabled'
const KomikkuNavigationRegionMenu = 'menu'
const KomikkuNavigationRegionPrevious = 'previous'
const KomikkuNavigationRegionNext = 'next'
const KomikkuNavigationRegionLeft = 'left'
const KomikkuNavigationRegionRight = 'right'
const ReaderFlowPaged = 'paged'
const ReaderFlowPagedVertical = 'paged-vertical'
const ReaderFlowScrolled = 'scrolled'
const ReaderFlowScrolledGaps = 'scrolled-gaps'
const ReaderDirectionDefault = 'default'
const ReaderDirectionLtr = 'ltr'
const ReaderDirectionRtl = 'rtl'
const ReaderThemePalettes = {
  light: {
    background: '#fbfaf8',
    foreground: '#1d1b18',
    accent: '#5b6fed',
  },
  sepia: {
    background: '#f3ead7',
    foreground: '#2b2118',
    accent: '#8a5a2b',
  },
  dusk: {
    background: '#252236',
    foreground: '#ece7f6',
    accent: '#d08cff',
  },
  dark: {
    background: '#111315',
    foreground: '#f2f0ea',
    accent: '#91a7ff',
  },
  black: {
    background: '#000000',
    foreground: '#f3f3f3',
    accent: '#7dd3fc',
  },
}

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

const readerThemePalette = theme =>
  ReaderThemePalettes[theme] || ReaderThemePalettes.light

const readerFlowMode = settings => {
  if (settings?.flowMode === ReaderFlowPagedVertical) return ReaderFlowPagedVertical
  if (settings?.flowMode === ReaderFlowScrolled) return ReaderFlowScrolled
  if (settings?.flowMode === ReaderFlowScrolledGaps) return ReaderFlowScrolledGaps
  if (settings?.paged === false) return ReaderFlowScrolled
  return ReaderFlowPaged
}

const readerFoliateFlow = flowMode =>
  flowMode === ReaderFlowScrolled || flowMode === ReaderFlowScrolledGaps
    ? 'scrolled'
    : 'paginated'

const readerDirectionMode = settings => {
  if (settings?.direction === ReaderDirectionLtr) return ReaderDirectionLtr
  if (settings?.direction === ReaderDirectionRtl) return ReaderDirectionRtl
  return ReaderDirectionDefault
}

// Ported from Komikku's ViewerNavigation plus L/Kindlish/Edge/RightAndLeft region classes.
const komikkuNavigationRegion = (left, top, right, bottom, type) => ({
  left,
  top,
  right,
  bottom,
  type,
})

const komikkuConstantMenuRegion = komikkuNavigationRegion(
  0,
  0,
  1,
  0.05,
  KomikkuNavigationRegionMenu
)

const komikkuRegionContains = (region, x, y) =>
  x >= region.left && x <= region.right && y >= region.top && y <= region.bottom

const komikkuRegionSize = smallerTapZone => smallerTapZone ? 0.25 : 0.33

const komikkuDefaultNavigationMode = flowMode =>
  flowMode === ReaderFlowPagedVertical ||
  flowMode === ReaderFlowScrolled ||
  flowMode === ReaderFlowScrolledGaps
    ? ReaderTapZoneLShaped
    : ReaderTapZoneRightLeft

const komikkuNavigationRegions = (
  tapZoneMode,
  smallerTapZone = false,
  flowMode = ReaderFlowPaged
) => {
  const mode = tapZoneMode === ReaderTapZoneDefault
    ? komikkuDefaultNavigationMode(flowMode)
    : tapZoneMode
  const regionSize1 = komikkuRegionSize(smallerTapZone)
  const regionSize2 = 1 - regionSize1
  switch (mode) {
    case ReaderTapZoneLShaped:
      return [
        komikkuNavigationRegion(0, regionSize1, regionSize1, regionSize2, KomikkuNavigationRegionPrevious),
        komikkuNavigationRegion(0, 0, 1, regionSize1, KomikkuNavigationRegionPrevious),
        komikkuNavigationRegion(regionSize2, regionSize1, 1, regionSize2, KomikkuNavigationRegionNext),
        komikkuNavigationRegion(0, regionSize2, 1, 1, KomikkuNavigationRegionNext),
      ]
    case ReaderTapZoneKindle:
      return [
        komikkuNavigationRegion(regionSize1, regionSize1, 1, 1, KomikkuNavigationRegionNext),
        komikkuNavigationRegion(0, regionSize1, regionSize1, 1, KomikkuNavigationRegionPrevious),
      ]
    case ReaderTapZoneEdge:
      return [
        komikkuNavigationRegion(0, 0, regionSize1, 1, KomikkuNavigationRegionNext),
        komikkuNavigationRegion(regionSize1, regionSize2, regionSize2, 1, KomikkuNavigationRegionPrevious),
        komikkuNavigationRegion(regionSize2, 0, 1, 1, KomikkuNavigationRegionNext),
      ]
    case ReaderTapZoneRightLeft:
      return [
        komikkuNavigationRegion(0, 0, regionSize1, 1, KomikkuNavigationRegionLeft),
        komikkuNavigationRegion(regionSize2, 0, 1, 1, KomikkuNavigationRegionRight),
      ]
    case ReaderTapZoneDisabled:
      return []
    default:
      return komikkuNavigationRegions(ReaderTapZoneDefault, smallerTapZone, flowMode)
  }
}

const komikkuTapAction = (
  tapZoneMode,
  x,
  y,
  smallerTapZone = false,
  flowMode = ReaderFlowPaged
) => {
  const regions = komikkuNavigationRegions(tapZoneMode, smallerTapZone, flowMode)
  const region = regions.find(candidate => komikkuRegionContains(candidate, x, y))
  if (region) return region.type
  if (komikkuRegionContains(komikkuConstantMenuRegion, x, y)) return KomikkuNavigationRegionMenu
  return KomikkuNavigationRegionMenu
}

const readerAssetUrl = path => new URL(path, document.baseURI).href

const readerFontFaceCss = () => `
  @font-face {
    font-family: 'Navic Literata';
    src: url('${readerAssetUrl('fonts/navic-literata-regular.ttf')}') format('truetype');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
  @font-face {
    font-family: 'Navic Atkinson Hyperlegible';
    src: url('${readerAssetUrl('fonts/navic-atkinson-hyperlegible-regular.otf')}') format('opentype');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
  @font-face {
    font-family: 'Navic OpenDyslexic';
    src: url('${readerAssetUrl('fonts/navic-opendyslexic-regular.otf')}') format('opentype');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
`

const readerParagraphSpacingEm = settings => {
  const percent = Number(settings.paragraphSpacingPercent)
  const normalized = Number.isFinite(percent)
    ? Math.min(200, Math.max(0, percent))
    : 0
  return `${normalized / 100}em`
}

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
  readerTapZoneMode = ReaderTapZoneDefault
  readerFlowModeValue = ReaderFlowPaged
  readerDirectionModeValue = ReaderDirectionDefault
  smallerTapZone = false
  originalBookDir = null
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
      case 'goToProgress':
        return this.goToProgress(command.progress)
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
    this.originalBookDir = null
    this.readerDirectionModeValue = ReaderDirectionDefault
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

  async goToProgress(progress) {
    if (!this.view) return
    const numericProgress = Number(progress)
    const fraction = Number.isFinite(numericProgress)
      ? Math.min(1, Math.max(0, numericProgress))
      : 0
    try {
      log('progress-seek:start', fraction)
      if (typeof this.view?.goToFraction === 'function') {
        await this.view.goToFraction(fraction)
      } else {
        await this.view.goTo({ fraction })
      }
      this.applyReaderViewportLayout('progress-seek')
      requestAnimationFrame(() => {
        this.logContentLayout('progress-seek')
        log('progress-seek:done', fraction)
      })
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

  attachCenterTapGesture(doc) {
    if (!doc?.defaultView || doc.defaultView.__navicCenterTapGestureAttached) return
    doc.defaultView.__navicCenterTapGestureAttached = true
    doc.addEventListener('click', async event => {
      if (event.defaultPrevented || event.button !== 0) return
      if (event.target?.closest?.('a,button,input,textarea,select,summary,[role="button"]')) return
      const selection = doc.getSelection?.()
      if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return
      const tapZone = this.readerTapZone(event, doc)
      if (!tapZone) return
      event.preventDefault()
      if (tapZone === 'previous') {
        await this.previousPage()
        return
      }
      if (tapZone === 'next') {
        await this.nextPage()
        return
      }
      if (tapZone === 'left') {
        if (this.effectiveReaderDirection() === ReaderDirectionRtl) await this.nextPage()
        else await this.previousPage()
        return
      }
      if (tapZone === 'right') {
        if (this.effectiveReaderDirection() === ReaderDirectionRtl) await this.previousPage()
        else await this.nextPage()
        return
      }
      log('center-tap')
      post({ type: 'readerCenterTap' })
    }, { passive: false })
  }

  readerTapZone(event, doc) {
    const win = doc?.defaultView || window
    const width = Math.max(1, win.innerWidth || doc?.documentElement?.clientWidth || 0)
    const height = Math.max(1, win.innerHeight || doc?.documentElement?.clientHeight || 0)
    const x = event.clientX
    const y = event.clientY
    if (!Number.isFinite(x) || !Number.isFinite(y)) return null
    const xFraction = x / width
    const yFraction = y / height
    return komikkuTapAction(
      this.readerTapZoneMode,
      xFraction,
      yFraction,
      this.smallerTapZone,
      this.readerFlowModeValue
    )
  }

  effectiveReaderDirection() {
    if (this.readerDirectionModeValue === ReaderDirectionLtr || this.readerDirectionModeValue === ReaderDirectionRtl) {
      return this.readerDirectionModeValue
    }
    return this.view?.book?.dir === ReaderDirectionRtl ? ReaderDirectionRtl : ReaderDirectionLtr
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
    if (typeof settings.tapZone === 'string') this.readerTapZoneMode = settings.tapZone || ReaderTapZoneDefault
    this.smallerTapZone = settings.smallerTapZone === true
    if (settings.fontSizePercent) rootStyle.setProperty('--reader-font-size', `${settings.fontSizePercent}%`)
    if (settings.lineHeight) rootStyle.setProperty('--reader-line-height', String(settings.lineHeight))
    rootStyle.setProperty('--reader-paragraph-spacing', readerParagraphSpacingEm(settings))
    const palette = readerThemePalette(settings.theme)
    rootStyle.setProperty('--reader-background', palette.background)
    rootStyle.setProperty('--reader-foreground', palette.foreground)
    rootStyle.setProperty('--reader-accent', palette.accent)
    const flowMode = readerFlowMode(settings)
    this.readerFlowModeValue = flowMode
    rootStyle.setProperty('--reader-scroll-gap', flowMode === ReaderFlowScrolledGaps ? '1.25rem' : '0rem')
    this.view?.renderer?.setAttribute('flow', readerFoliateFlow(flowMode))
    this.readerDirectionModeValue = readerDirectionMode(settings)
    this.applyReaderDirection(this.readerDirectionModeValue)
    this.applyReaderViewportLayout('settings')
    this.view?.renderer?.setStyles?.(readerContentCss(settings))
  }

  applyReaderDirection(direction, rerender = true) {
    const normalized = direction === ReaderDirectionLtr || direction === ReaderDirectionRtl
      ? direction
      : ReaderDirectionDefault
    if (this.view?.book) {
      if (this.originalBookDir === null) this.originalBookDir = this.view.book.dir || ''
      this.view.book.dir = normalized === ReaderDirectionDefault ? this.originalBookDir : normalized
    }
    for (const doc of this.contentDocuments()) {
      this.applyDocumentDirection(doc, normalized)
    }
    if (rerender) this.view?.renderer?.render?.()
  }

  applyDocumentDirection(doc, direction) {
    if (!doc) return
    for (const element of [doc.documentElement, doc.body].filter(Boolean)) {
      if (direction === ReaderDirectionDefault) {
        if (element.dataset.navicOriginalDir !== undefined) {
          const original = element.dataset.navicOriginalDir
          if (original) element.setAttribute('dir', original)
          else element.removeAttribute('dir')
          delete element.dataset.navicOriginalDir
        }
      } else {
        if (element.dataset.navicOriginalDir === undefined) {
          element.dataset.navicOriginalDir = element.getAttribute('dir') || ''
        }
        element.setAttribute('dir', direction)
      }
    }
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
    this.applyReaderDirection(this.readerDirectionModeValue, false)
    const contents = this.view?.renderer?.getContents?.() || []
    for (const content of contents) {
      const doc = content.doc
      if (!doc) continue
      this.attachScrolledEdgeTurnGestures(doc)
      this.attachCenterTapGesture(doc)
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

const readerTypographyCss = settings => settings.publisherStyles === true
  ? ''
  : `
  ${readerFlowMode(settings) === ReaderFlowPagedVertical ? `
  html, body {
    writing-mode: vertical-rl !important;
  }
  ` : ''}
  body {
    line-height: ${settings.lineHeight || 1.55} !important;
    font-family: ${settings.fontFamily || 'system-ui, sans-serif'} !important;
    margin-inline: ${settings.marginPercent || 0}% !important;
    padding-block: var(--reader-scroll-gap, 0rem) !important;
  }
  p {
    margin-block-start: 0 !important;
    margin-block-end: var(--reader-paragraph-spacing, 0em) !important;
  }
`

const readerContentCss = settings => `
  ${readerFontFaceCss()}
  html {
    color: var(--reader-foreground) !important;
    background: var(--reader-background) !important;
    font-size: ${settings.fontSizePercent || 100}%;
  }
  ${readerTypographyCss(settings)}
  a:any-link {
    color: inherit !important;
    text-decoration: none !important;
    border-bottom: 0 !important;
    box-shadow: none !important;
  }
  a:any-link::after {
    content: ' »';
    font-size: 0.72em;
    font-weight: 700;
    line-height: 0;
    vertical-align: sub;
    white-space: nowrap;
    opacity: 0.72;
  }
  a:any-link:empty::after {
    content: '';
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
