import {
  ReaderDirectionDefault,
  ReaderDirectionLtr,
  ReaderDirectionRtl,
  ReaderDocumentThemeStyleId,
  readerDirectionMode,
  readerThemeKey,
  readerThemePalette,
} from './navic-reader-settings.js'
import {
  applyReaderParagraphSpacing,
  isThemeBackgroundMediaElement,
  normalizeReaderInlineTypography,
  normalizeReaderLineFragmentParagraphs,
  readerContentCss,
  readerParagraphSpacingEm,
  readerNormalizeChapterOpeningMargins,
} from './navic-reader-typography.js'
import { stableHash } from './navic-reader-identity.js'

const originalPublicationDirections = new WeakMap()
const RasterDecorationSelectors = Object.freeze([
  '[data-navic-surface-paper-texture-layer="true"]',
  '[data-navic-moving-page-paper-texture-layer="true"]',
  '[data-navic-moving-page-border-overlay-layer="true"]',
  '[data-navic-moving-page-stain-overlay-layer="true"]',
  '[data-navic-surface-spread-gutter-overlay-layer="true"]',
])
const loadedRasterAssets = new WeakMap()
const rasterAssetVerificationRequests = new WeakMap()

const setStylesImportant = (element, styles) => {
  if (!element) return
  for (const [property, value] of Object.entries(styles)) {
    element.style.setProperty(property, value, 'important')
  }
}

const normalizedDirection = value =>
  value === ReaderDirectionLtr || value === ReaderDirectionRtl
    ? value
    : ReaderDirectionDefault

export const applyReaderPublicationRenderDirection = (view, direction) => {
  if (!view?.book) return ReaderDirectionDefault
  const normalized = normalizedDirection(direction)
  if (!originalPublicationDirections.has(view)) {
    originalPublicationDirections.set(view, String(view.book.dir || ''))
  }
  view.book.dir = normalized === ReaderDirectionDefault
    ? originalPublicationDirections.get(view)
    : normalized
  return normalized
}

export const applyReaderDocumentRenderDirection = (doc, direction) => {
  if (!doc) return
  const normalized = normalizedDirection(direction)
  for (const element of [doc.documentElement, doc.body].filter(Boolean)) {
    if (normalized === ReaderDirectionDefault) {
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
      element.setAttribute('dir', normalized)
    }
  }
}

export const applyReaderContentDocumentRenderProfile = (doc, settings = {}) => {
  if (!doc?.documentElement) return false
  const palette = readerThemePalette(settings.theme)
  const root = doc.documentElement
  const body = doc.body
  const styleHost = doc.head || root
  applyReaderDocumentRenderDirection(doc, readerDirectionMode(settings))
  normalizeReaderLineFragmentParagraphs(doc, settings)
  applyReaderParagraphSpacing(doc, settings)
  root.dataset.navicReaderTheme = readerThemeKey(settings.theme)
  delete root.dataset.navicPaperTextureKey
  delete root.dataset.navicPaperTextureAsset
  let themeStyle = doc.getElementById(ReaderDocumentThemeStyleId)
  if (!themeStyle) {
    themeStyle = doc.createElement('style')
    themeStyle.id = ReaderDocumentThemeStyleId
    styleHost.append(themeStyle)
  }
  themeStyle.textContent = readerContentCss(settings)
  normalizeReaderInlineTypography(doc, settings)
  readerNormalizeChapterOpeningMargins(doc, settings)
  for (const element of [root, body].filter(Boolean)) {
    setStylesImportant(element, {
      '--reader-background': palette.background,
      '--reader-foreground': palette.foreground,
      '--reader-accent': palette.accent,
      '--theme-bg-color': palette.background,
      '--reader-paragraph-spacing': readerParagraphSpacingEm(settings),
      background: palette.background,
      'background-color': palette.background,
      'background-image': 'none',
      color: palette.foreground,
    })
  }
  for (const element of doc.querySelectorAll('[style*="background"], [bgcolor]')) {
    if (!element || element === root || element === body || isThemeBackgroundMediaElement(element)) {
      continue
    }
    if (element.hasAttribute('bgcolor')) {
      if (element.dataset.navicOriginalBgcolor === undefined) {
        element.dataset.navicOriginalBgcolor = element.getAttribute('bgcolor') || ''
      }
      element.removeAttribute('bgcolor')
    }
    setStylesImportant(element, {
      background: 'transparent',
      'background-color': 'transparent',
      'background-image': 'none',
    })
  }
  for (const element of doc.querySelectorAll('canvas, svg')) {
    setStylesImportant(element, { 'background-color': 'transparent' })
  }
  return true
}

const roundedRect = rect => Object.freeze({
  left: Math.round(Number(rect?.left) || 0),
  top: Math.round(Number(rect?.top) || 0),
  width: Math.round(Number(rect?.width) || 0),
  height: Math.round(Number(rect?.height) || 0),
})

const roundedSize = rect => Object.freeze({
  width: Math.round(Number(rect?.width) || 0),
  height: Math.round(Number(rect?.height) || 0),
})

const styleProof = (style, properties) => Object.freeze(Object.fromEntries(
  properties.map(property => [property, String(style?.getPropertyValue(property) || '')]),
))

const urlsFromCssValue = value => Array.from(
  String(value || '').matchAll(/url\(["']?([^"')]+)["']?\)/g),
  match => new URL(match[1], document.baseURI).href,
)

const RasterDecorationStyleProperties = Object.freeze([
  'display',
  'visibility',
  'opacity',
  'background-color',
  'background-image',
  'background-position',
  'background-size',
  'box-shadow',
  'mix-blend-mode',
  'transform',
])

const rasterDecorationNodeProof = element => {
  const style = getComputedStyle(element)
  return Object.freeze({
    tagName: element.tagName,
    className: String(element.className || ''),
    rect: roundedRect(element.getBoundingClientRect()),
    style: styleProof(style, RasterDecorationStyleProperties),
  })
}

const rasterDecorationProof = (root = document) => RasterDecorationSelectors.map(selector => {
  const element = root.querySelector(selector)
  if (!element) return Object.freeze({ selector, present: false })
  return Object.freeze({
    selector,
    present: true,
    nodes: Object.freeze([
      element,
      ...element.querySelectorAll('*'),
    ].map(rasterDecorationNodeProof)),
  })
})

export const readerRasterDecorationAssetUrls = (root = document) => Array.from(new Set(
  rasterDecorationProof(root).flatMap(layer => layer.present
    ? layer.nodes.flatMap(node => urlsFromCssValue(node.style['background-image']))
    : [],
  ),
)).sort()

const loadRasterImage = url => new Promise(resolve => {
  const image = new Image()
  image.onload = () => resolve(true)
  image.onerror = () => resolve(false)
  image.src = url
})

const rasterAssetUrlsEqual = (left, right) =>
  left.length === right.length && left.every((url, index) => right[index] === url)

export const waitForReaderRasterAssets = async (root = document) => {
  const urls = readerRasterDecorationAssetUrls(root)
  const request = Object.freeze({ urls: Object.freeze(urls) })
  rasterAssetVerificationRequests.set(root, request)
  const results = await Promise.all(urls.map(loadRasterImage))
  if (rasterAssetVerificationRequests.get(root) !== request) return false
  if (!rasterAssetUrlsEqual(readerRasterDecorationAssetUrls(root), urls)) return false
  if (results.some(loaded => !loaded)) {
    loadedRasterAssets.delete(root)
    return false
  }
  loadedRasterAssets.set(root, request.urls)
  return true
}

const transformedDocumentProof = doc => {
  const root = doc?.documentElement
  const body = doc?.body
  const win = doc?.defaultView
  if (!root || !body || !win) return null
  const rootStyle = win.getComputedStyle(root)
  const bodyStyle = win.getComputedStyle(body)
  const firstParagraph = doc.querySelector('p,[data-navic-paragraph-block="true"]')
  const paragraphStyle = firstParagraph ? win.getComputedStyle(firstParagraph) : null
  const heading = doc.querySelector('h1,h2,h3,h4,h5,h6')
  const inlineNormalized = doc.querySelector('[data-navic-inline-typography-normalized="true"]')
  return Object.freeze({
    rootDirection: root.getAttribute('dir') || '',
    bodyDirection: body.getAttribute('dir') || '',
    theme: root.dataset.navicReaderTheme || '',
    lineFragmentsNormalized: root.dataset.navicLineFragmentsNormalized === 'true',
    looseParagraphCount: doc.querySelectorAll('[data-navic-loose-text-paragraph="true"]').length,
    paragraphBlockCount: doc.querySelectorAll('[data-navic-paragraph-block="true"]').length,
    inlineTypographyNormalized: inlineNormalized != null,
    chapterOpeningCapped:
      heading?.getAttribute('data-navic-chapter-opening-margin-capped') === 'true',
    rootSize: roundedSize(root.getBoundingClientRect()),
    bodySize: roundedSize(body.getBoundingClientRect()),
    rootStyle: styleProof(rootStyle, [
      'direction',
      'font-family',
      'font-size',
      'line-height',
      'color',
      'background-color',
    ]),
    bodyStyle: styleProof(bodyStyle, [
      'direction',
      'font-family',
      'font-size',
      'line-height',
      'color',
      'background-color',
    ]),
    paragraphStyle: styleProof(paragraphStyle, [
      'display',
      'font-family',
      'font-size',
      'line-height',
      'margin-top',
      'margin-bottom',
    ]),
    chapterOpeningMargin: heading?.style?.getPropertyValue('margin-top') || '',
  })
}

export const ReaderRasterProfileAuthorityLiveRealized = 'live-realized-v1'
export const ReaderRasterProfileAuthorityPassiveRealized = 'passive-realized-v1'

export const readerLiveIssuedRasterPlan = target => {
  const publicationUrl = String(target?.publicationUrl || '')
  const paginationFingerprint = String(target?.paginationFingerprint || '')
  const viewportWidth = Math.max(1, Math.round(Number(target?.viewportWidth) || 1))
  const viewportHeight = Math.max(1, Math.round(Number(target?.viewportHeight) || 1))
  if (!publicationUrl || !paginationFingerprint) return null
  const render = target?.render && typeof target.render === 'object' ? target.render : null
  const settings = target?.readerSettings && typeof target.readerSettings === 'object'
    ? target.readerSettings
    : {}
  const layoutFingerprint = stableHash(JSON.stringify({
    render,
    mode: target?.layoutMode || 'single',
    pages: Array.isArray(target?.layoutPages) ? target.layoutPages : [],
    viewportWidth,
    viewportHeight,
  }))
  const decorationFingerprint = stableHash(JSON.stringify({
    theme: settings.theme || '',
    paperTextureEnabled: settings.paperTextureEnabled !== false,
    pageEdgesEnabled: settings.pageEdgesEnabled !== false,
    paperStainsEnabled: settings.paperStainsEnabled !== false,
    coverBackdropEnabled: settings.coverBackdropEnabled !== false,
  }))
  const rasterProfileKey = stableHash(JSON.stringify({
    publicationUrl,
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
    viewportWidth,
    viewportHeight,
  }))
  return Object.freeze({
    profileAuthority: ReaderRasterProfileAuthorityPassiveRealized,
    rasterProfileKey,
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
  })
}

export const readerRealizedRasterObservation = (view, target, root = document) => {
  const renderer = view?.renderer
  const index = Math.floor(Number(target?.spineIndex))
  const content = renderer?.getContents?.().find(entry => entry.index === index)
  const doc = content?.doc
  if (!renderer || !Number.isSafeInteger(index) || index < 0 || !doc?.documentElement ||
      (doc.fonts && doc.fonts.status !== 'loaded')) return null
  const documentProof = transformedDocumentProof(doc)
  if (!documentProof) return null
  const viewRect = view.getBoundingClientRect()
  const density = window.devicePixelRatio || 1
  const viewportWidth = Math.round(viewRect.width * density)
  const viewportHeight = Math.round(viewRect.height * density)
  if (viewportWidth <= 0 || viewportHeight <= 0) return null
  const decorationProof = rasterDecorationProof(root)
  const requiredAssetUrls = readerRasterDecorationAssetUrls(root)
  const loadedAssetUrls = loadedRasterAssets.get(root) || Object.freeze([])
  if (
    requiredAssetUrls.length !== loadedAssetUrls.length ||
    requiredAssetUrls.some((url, index) => loadedAssetUrls[index] !== url)
  ) return null
  const rendererProof = Object.freeze({
    publicationDirection: String(view.book?.dir || ''),
    sectionCount: Number(view.book?.sections?.length) || 0,
    flow: renderer.getAttribute('flow') || '',
    maxInlineSize: renderer.getAttribute('max-inline-size') || '',
    maxBlockSize: renderer.getAttribute('max-block-size') || '',
    maxColumnCount: renderer.getAttribute('max-column-count') || '',
    columnThreshold: renderer.getAttribute('column-threshold') || '',
    gap: renderer.getAttribute('gap') || '',
    contentGap: renderer.getAttribute('content-gap') || '',
    topMargin: renderer.getAttribute('top-margin') || '',
    bottomMargin: renderer.getAttribute('bottom-margin') || '',
    viewportWidth,
    viewportHeight,
  })
  const paginationFingerprint = stableHash(JSON.stringify(rendererProof))
  const layoutFingerprint = stableHash(JSON.stringify({ rendererProof, documentProof }))
  const decorationFingerprint = stableHash(JSON.stringify({
    layers: decorationProof,
    loadedAssetUrls,
  }))
  const rasterProfileKey = stableHash(JSON.stringify({
    publicationUrl: String(target.publicationUrl || ''),
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
    viewportWidth,
    viewportHeight,
  }))
  return Object.freeze({
    rasterProfileKey,
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
    viewportAndCaptureGeometry: Object.freeze({
      viewportWidth,
      viewportHeight,
      captureLeft: 0,
      captureTop: 0,
      captureRight: viewportWidth,
      captureBottom: viewportHeight,
    }),
    loadedAssetUrls,
    rendererProof,
    documentProof,
    decorationProof,
  })
}
