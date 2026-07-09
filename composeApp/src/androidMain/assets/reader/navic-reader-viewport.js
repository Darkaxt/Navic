import {
  readerFlowMode,
  readerFoliateFlow,
} from './navic-reader-settings.js'
import {
  readerRoot,
  log,
  readerAdaptiveFoliatePageBox,
  readerPageShellGeometryForViewport,
  readerPageShellRectStyle,
  readerShellGeometryDiagnosticState,
  readerTrace,
  readerViewportSize,
  setStylesImportant,
  updateReaderShellCoverLayer,
} from './navic-reader-helpers.js'

const ReaderPdfFitWidth = 'width'
const ReaderPdfFitPage = 'page'
const ReaderPdfFitHeight = 'height'
const ReaderPdfFitOriginal = 'original'
const ReaderPdfPageGapMaxPercent = 48

const normalizedReaderPdfFitMode = value =>
  [ReaderPdfFitWidth, ReaderPdfFitPage, ReaderPdfFitHeight, ReaderPdfFitOriginal].includes(value)
    ? value
    : ReaderPdfFitWidth

const readerPdfZoomAttribute = value => {
  switch (normalizedReaderPdfFitMode(value)) {
    case ReaderPdfFitPage:
      return 'fit-page'
    case ReaderPdfFitHeight:
      return 'fit-height'
    case ReaderPdfFitOriginal:
      return '1'
    case ReaderPdfFitWidth:
    default:
      return 'fit-width'
  }
}

const normalizedReaderPdfPageGapPercent = value => {
  const gap = Number.parseInt(value, 10)
  if (!Number.isFinite(gap)) return 0
  return Math.min(ReaderPdfPageGapMaxPercent, Math.max(0, gap))
}

function applyReaderViewportLayout(label = 'unknown') {
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
  const fixedLayout = this.view?.isFixedLayout === true || renderer?.localName === 'foliate-fxl'
  const shellGeometry = readerPageShellGeometryForViewport(this.readerSettings, {
    flowMode: this.readerFlowModeValue || readerFlowMode(this.readerSettings),
  })
  const rendererRect = fixedLayout
    ? { left: 0, top: 0, width, height }
    : shellGeometry.shellRect
  const rendererRectStyle = readerPageShellRectStyle(rendererRect)
  const shellRectStyle = readerPageShellRectStyle(shellGeometry.shellRect)
  const pageBoxSettings = shellGeometry.renderer.pageBoxMaxColumnCount == null
    ? this.readerSettings
    : { ...this.readerSettings, maxColumnCount: shellGeometry.renderer.pageBoxMaxColumnCount }
  const pageBox = readerAdaptiveFoliatePageBox({
    width: fixedLayout ? width : shellGeometry.renderer.pageBoxWidth,
    height: fixedLayout ? height : shellGeometry.renderer.height,
  }, pageBoxSettings)
  this.readerPageShellGeometry = shellGeometry
  readerRoot.dataset.navicReaderShellGeometryMode = shellGeometry.mode
  readerRoot.dataset.navicReaderShellGutterWidth = String(shellGeometry.edgeInsets?.gutter || 0)
  readerRoot.dataset.navicReaderShellGeometry = JSON.stringify(readerShellGeometryDiagnosticState(shellGeometry, 'reader-shell-geometry'))
  setStylesImportant(renderer, {
    position: 'absolute',
    inset: 'auto',
    display: 'block',
    ...rendererRectStyle,
    'min-width': rendererRectStyle.width,
    'min-height': rendererRectStyle.height,
    overflow: fixedLayout ? 'auto' : 'hidden',
  })
  if (renderer && !fixedLayout) {
    renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)
    renderer.setAttribute('max-block-size', pageBox.maxBlockSize)
    renderer.setAttribute('max-column-count', pageBox.maxColumnCount)
    renderer.setAttribute('column-threshold', pageBox.columnThreshold)
    renderer.setAttribute('top-margin', `${shellGeometry.renderer.topMargin}px`)
    renderer.setAttribute('bottom-margin', `${shellGeometry.renderer.bottomMargin}px`)
    renderer.setAttribute('gap', `${shellGeometry.renderer.gapPercent}%`)
    renderer.dataset.navicAdaptivePageBox = JSON.stringify(pageBox)
    renderer.dataset.navicReaderShellGeometryMode = shellGeometry.mode
    renderer.dataset.navicReaderShellGutterWidth = String(shellGeometry.edgeInsets?.gutter || 0)
    renderer.dataset.navicReaderShellRect = JSON.stringify(shellGeometry.shellRect)
    renderer.dataset.navicReaderShellContentRects = JSON.stringify(shellGeometry.contentRects)
    renderer.style.setProperty('--navic-reader-shell-left', shellRectStyle.left)
    renderer.style.setProperty('--navic-reader-shell-top', shellRectStyle.top)
    renderer.style.setProperty('--navic-reader-shell-width', shellRectStyle.width)
    renderer.style.setProperty('--navic-reader-shell-height', shellRectStyle.height)
  }
  this.applyThemeToLoadedContent?.(this.readerSettings)
  this.applyPdfImageSettings(this.readerSettings)
  if (renderer) requestAnimationFrame(() => renderer?.render?.())
  if (this.shellCoverVisible && this.shellCoverLayer && this.shellCoverBlobUrl) {
    updateReaderShellCoverLayer(
      this.shellCoverLayer,
      this.shellCoverBlobUrl,
      this.readerSettings,
      this.view?.book?.metadata?.title || ''
    )
  }
  this.renderSurfacePaperTextureLayers()
  this.renderTapZoneOverlayLayer()
  this.preloadPageDragPreviewTargets?.(`viewport-layout:${label}`)
  log('viewport-layout', `label=${label}`, `${width}x${height}`)
  readerTrace('reader-shell-geometry', readerShellGeometryDiagnosticState(shellGeometry, `viewport-layout:${label}`))
}


function applyReaderViewportLayoutToProfilerView(profileView, settings = this.readerSettings) {
  if (!profileView) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  setStylesImportant(profileView, {
    position: 'fixed',
    inset: '0px',
    display: 'block',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    overflow: 'hidden',
    visibility: 'hidden',
    opacity: '0',
    'pointer-events': 'none',
    'z-index': '-1',
  })
  const renderer = profileView?.renderer
  const shellGeometry = readerPageShellGeometryForViewport(settings, {
    flowMode: readerFlowMode(settings),
  })
  const rendererRectStyle = readerPageShellRectStyle(shellGeometry.shellRect)
  const pageBoxSettings = shellGeometry.renderer.pageBoxMaxColumnCount == null
    ? settings
    : { ...settings, maxColumnCount: shellGeometry.renderer.pageBoxMaxColumnCount }
  const pageBox = readerAdaptiveFoliatePageBox({
    width: shellGeometry.renderer.pageBoxWidth,
    height: shellGeometry.renderer.height,
  }, pageBoxSettings)
  setStylesImportant(renderer, {
    position: 'absolute',
    inset: 'auto',
    display: 'block',
    ...rendererRectStyle,
    'min-width': rendererRectStyle.width,
    'min-height': rendererRectStyle.height,
    overflow: 'hidden',
  })
  if (renderer) {
    renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)
    renderer.setAttribute('max-block-size', pageBox.maxBlockSize)
    renderer.setAttribute('max-column-count', pageBox.maxColumnCount)
    renderer.setAttribute('column-threshold', pageBox.columnThreshold)
    renderer.setAttribute('top-margin', `${shellGeometry.renderer.topMargin}px`)
    renderer.setAttribute('bottom-margin', `${shellGeometry.renderer.bottomMargin}px`)
    renderer.setAttribute('gap', `${shellGeometry.renderer.gapPercent}%`)
    renderer.dataset.navicAdaptivePageBox = JSON.stringify(pageBox)
    renderer.dataset.navicReaderShellGeometryMode = shellGeometry.mode
    renderer.dataset.navicReaderShellGutterWidth = String(shellGeometry.edgeInsets?.gutter || 0)
    renderer.dataset.navicReaderShellRect = JSON.stringify(shellGeometry.shellRect)
    renderer.dataset.navicReaderShellContentRects = JSON.stringify(shellGeometry.contentRects)
  }
  renderer?.setAttribute?.('flow', readerFoliateFlow(readerFlowMode(settings)))
  renderer?.render?.()
}


function applyPdfImageSettings(settings = this.readerSettings) {
  const renderer = this.view?.renderer
  if (!renderer || this.view?.isFixedLayout !== true) return
  const fitMode = normalizedReaderPdfFitMode(settings?.pdfFitMode)
  const cropBorders = settings?.pdfCropBorders === true
  const gapPercent = normalizedReaderPdfPageGapPercent(settings?.pdfPageGapPercent)
  const viewport = readerViewportSize()
  const gapPx = Math.round(Math.max(1, viewport.height || 0) * gapPercent / 100)
  renderer.setAttribute('zoom', readerPdfZoomAttribute(fitMode))
  renderer.setAttribute('page-gap', String(gapPx))
  if (cropBorders) renderer.setAttribute('crop-borders', 'true')
  else renderer.removeAttribute('crop-borders')
  renderer.setAttribute('data-navic-pdf-fit-mode', fitMode)
  renderer.setAttribute('data-navic-pdf-crop-borders', cropBorders ? 'true' : 'false')
  renderer.setAttribute('data-navic-pdf-page-gap-percent', String(gapPercent))
  renderer.setAttribute('data-navic-pdf-page-gap-px', String(gapPx))
  renderer.style.setProperty('--reader-pdf-page-gap', `${gapPx}px`)
  renderer.style.setProperty('--reader-pdf-crop-scale', cropBorders ? '1.045' : '1')
  readerTrace('pdf-settings:apply', {
    fitMode,
    zoom: renderer.getAttribute('zoom'),
    cropBorders,
    gapPercent,
    gapPx,
  })
}

export const NavicReaderViewportMethods = {
  applyReaderViewportLayout,
  applyReaderViewportLayoutToProfilerView,
  applyPdfImageSettings,
}
