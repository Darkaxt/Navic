import { Overlayer } from './vendor/foliate-js/overlayer.js'

export const ReaderMediaOverlayRangeAttribute = 'data-navic-media-overlay-range'
export const ReaderMediaOverlayActiveRangeKey = 'navic-media-overlay-active'

const ReaderSvgNamespace = 'http://www.w3.org/2000/svg'

export const readerCssColorFromArgb = (argb, fallback) => {
  const value = Number(argb)
  if (!Number.isFinite(value)) return fallback
  const unsigned = value >>> 0
  const alpha = ((unsigned >>> 24) & 255) / 255
  const red = (unsigned >>> 16) & 255
  const green = (unsigned >>> 8) & 255
  const blue = unsigned & 255
  return `rgba(${red}, ${green}, ${blue}, ${Math.round(alpha * 1000) / 1000})`
}

export const readerMediaOverlayUnwrapRangeMarker = marker => {
  const parent = marker?.parentNode
  if (!parent) return false
  while (marker.firstChild) {
    parent.insertBefore(marker.firstChild, marker)
  }
  parent.removeChild(marker)
  parent.normalize?.()
  return true
}

export const readerDrawMediaOverlaySelection = (rects, options = {}) => {
  const { color = 'red', padding = 0 } = options
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  group.setAttribute('fill', color)
  group.style.mixBlendMode = 'var(--overlayer-highlight-blend-mode, normal)'
  for (const { left, top, height, width } of rects) {
    const element = document.createElementNS(ReaderSvgNamespace, 'rect')
    element.setAttribute('x', left - padding)
    element.setAttribute('y', top - padding)
    element.setAttribute('height', height + padding * 2)
    element.setAttribute('width', width + padding * 2)
    group.append(element)
  }
  return group
}

export const readerDrawMediaOverlayMarker = (rects, options = {}) => {
  const { color = 'red', padding = 0 } = options
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  group.setAttribute('fill', color)
  group.style.mixBlendMode = 'var(--overlayer-highlight-blend-mode, normal)'
  for (const { left, top, height, width } of rects) {
    const markerTop = top - padding
    const markerLeft = left - padding
    const markerWidth = width + padding * 2
    const markerHeight = height + padding * 2
    const slant = Math.min(Math.max(markerHeight * 0.36, 2), 12)
    const element = document.createElementNS(ReaderSvgNamespace, 'polygon')
    element.setAttribute(
      'points',
      [
        `${markerLeft + slant},${markerTop}`,
        `${markerLeft + markerWidth},${markerTop}`,
        `${markerLeft + markerWidth - slant},${markerTop + markerHeight}`,
        `${markerLeft},${markerTop + markerHeight}`,
      ].join(' ')
    )
    group.append(element)
  }
  return group
}

export const readerDrawNoteAnnotation = (rects, options = {}) => {
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  group.setAttribute('data-navic-note-annotation', 'true')
  group.append(
    Overlayer.highlight(rects, options),
    Overlayer.squiggly(rects, {
      color: options.noteColor || options.color || '#b86e00',
      width: 1.6,
      padding: 2,
      writingMode: options.writingMode,
    })
  )
  return group
}
