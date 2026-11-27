import { closeReaderDocumentFigures, toggleReaderDocumentFigure } from './navic-reader-render-profile.js'

// Native bridge for illustrations in the loaded section documents: living plates (#77) and
// document figures (#82). Coordinates are CSS px of the WebView viewport.

const readerLoadedContentDocuments = runtime => {
  const renderer = runtime?.view?.renderer
  const contents = typeof renderer?.getContents === 'function' ? (renderer.getContents() || []) : []
  return contents.map(content => content?.doc).filter(doc => doc?.body)
}

// #77: the illustration plates fully inside the WebView viewport, for the native living-plate
// overlay (the image box is the contained image itself, readerIllustrationCss), plus the document
// figures on screen, for the native tap that shows their text.
export const readerLivingPlateRectsJson = runtime => {
  const plates = []
  const figures = []
  let pending = false
  try {
    const renderer = runtime?.view?.renderer
    const contents = typeof renderer?.getContents === 'function' ? (renderer.getContents() || []) : []
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight
    for (const content of contents) {
      const doc = content?.doc
      const frame = doc?.defaultView?.frameElement
      if (!doc?.body || !frame?.isConnected) continue
      const frameRect = frame.getBoundingClientRect()
      const scaleX = frame.offsetWidth > 0 ? frameRect.width / frame.offsetWidth : 1
      const scaleY = frame.offsetHeight > 0 ? frameRect.height / frame.offsetHeight : 1
      for (const image of doc.body.querySelectorAll('img[data-navic-illustration-image]')) {
        const sceneId = image.parentElement?.getAttribute('data-navic-illustration')
        const loopSha256 = image.dataset.navicLivingLoop || ''
        const advancedLoopSha256 = image.dataset.navicLivingAdvanced || ''
        if (!sceneId || !(loopSha256 || advancedLoopSha256)) continue
        if (!image.complete) {
          pending = true
          continue
        }
        const rect = image.getBoundingClientRect()
        const left = frameRect.left + rect.left * scaleX
        const top = frameRect.top + rect.top * scaleY
        const width = rect.width * scaleX
        const height = rect.height * scaleY
        if (width < 2 || height < 2) continue
        if (left < -1 || top < -1 || left + width > viewportWidth + 1 || top + height > viewportHeight + 1) continue
        plates.push({
          sceneId,
          sha256: image.dataset.navicSha256 || '',
          loopSha256,
          revealSha256: image.dataset.navicLivingReveal || '',
          advancedLoopSha256,
          left,
          top,
          width,
          height,
        })
      }
      // Document figures (#82) on screen, for the native tap that shows their text.
      for (const figure of doc.body.querySelectorAll('figure[data-navic-document-figure]')) {
        const sceneId = figure.getAttribute('data-navic-illustration')
        if (!sceneId) continue
        const rect = figure.getBoundingClientRect()
        const left = Math.max(0, frameRect.left + rect.left * scaleX)
        const top = Math.max(0, frameRect.top + rect.top * scaleY)
        const right = Math.min(viewportWidth, frameRect.left + rect.right * scaleX)
        const bottom = Math.min(viewportHeight, frameRect.top + rect.bottom * scaleY)
        if (right - left < 8 || bottom - top < 8) continue
        figures.push({
          sceneId,
          revealed: figure.hasAttribute('data-navic-figure-revealed'),
          left,
          top,
          width: right - left,
          height: bottom - top,
        })
      }
    }
  } catch {
    plates.length = 0
    figures.length = 0
  }
  return JSON.stringify({ dpr: window.devicePixelRatio || 1, plates, figures, pending })
}

export const readerToggleDocumentFigure = (runtime, sceneId) =>
  readerLoadedContentDocuments(runtime).some(doc => toggleReaderDocumentFigure(doc, String(sceneId || '')))

export const readerCloseDocumentFigures = runtime =>
  readerLoadedContentDocuments(runtime).reduce((count, doc) => count + closeReaderDocumentFigures(doc), 0)

// Masked in the live page-turn check: living plates, whose curl recording carries them in colour
// where the page raster draws them grey, and document figures showing their text (#82), whose
// raster draws the figure.
export const readerIllustrationCaptureMaskRects = runtime => {
  try {
    const { plates, figures } = JSON.parse(readerLivingPlateRectsJson(runtime))
    const revealed = (figures || []).filter(figure => figure.revealed)
    return [...(plates || []), ...revealed].map(({ left, top, width, height }) => ({ left, top, width, height }))
  } catch {
    return []
  }
}
