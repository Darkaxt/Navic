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

export const readerDrawWhispersyncCueOrdinal = (rects, options = {}) => {
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  const first = rects[0]
  if (!first) return group
  const sourceOrdinal = Number(options.sourceOrdinal)
  const radius = 7
  const centerX = first.left + radius
  const centerY = first.top - 3
  const circle = (state, ringRadius, attributes = {}) => {
    const element = document.createElementNS(ReaderSvgNamespace, 'circle')
    element.setAttribute('cx', centerX)
    element.setAttribute('cy', centerY)
    element.setAttribute('r', ringRadius)
    if (state) element.setAttribute('data-navic-cue-visual-state', state)
    for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, value)
    group.append(element)
    return element
  }
  group.setAttribute('data-navic-cue-source-ordinal', String(sourceOrdinal))
  group.setAttribute('data-navic-cue-baseline-offset', String(centerY - first.bottom))
  group.setAttribute('role', 'button')
  group.style.pointerEvents = 'all'
  group.style.cursor = 'pointer'

  circle('mapped', radius, {
    fill: options.audioActive ? 'rgba(27, 138, 88, 0.96)' : 'rgba(34, 34, 34, 0.88)',
    stroke: 'rgba(255, 255, 255, 0.96)',
    'stroke-width': '1',
  })
  if (options.audioActive) {
    circle('audio-active', radius - 2, {
      fill: 'rgba(47, 191, 113, 0.9)',
      stroke: 'none',
    })
  }
  if (options.renderedHighlight) {
    circle('rendered-highlight', radius - 1, {
      fill: 'none',
      stroke: '#e879f9',
      'stroke-width': '1.8',
    })
  }
  if (options.prepared) {
    circle('prepared', radius + 2, {
      fill: 'none',
      stroke: '#38bdf8',
      'stroke-width': '1.5',
    })
  }
  if (options.requested) {
    circle('requested', radius + 4, {
      fill: 'none',
      stroke: '#fbbf24',
      'stroke-width': '1.7',
      'stroke-dasharray': '3 2',
    })
  }

  const circumference = 2 * Math.PI * (radius + 6)
  const determinateRing = circle(null, radius + 6, {
    fill: 'none',
    stroke: '#f8fafc',
    'stroke-width': '2',
    'stroke-linecap': 'round',
    'stroke-dasharray': String(circumference),
    'stroke-dashoffset': String(circumference),
    'data-navic-cue-hold-ring': 'determinate',
    'data-navic-cue-hold-progress-state': 'idle',
    opacity: '0',
  })
  determinateRing.style.transformOrigin = `${centerX}px ${centerY}px`
  determinateRing.style.transform = 'rotate(-90deg)'

  const pendingRing = circle(null, radius + 6, {
    fill: 'none',
    stroke: '#fbbf24',
    'stroke-width': '2',
    'stroke-linecap': 'round',
    'stroke-dasharray': '5 4',
    'data-navic-cue-hold-ring': 'indeterminate',
    'data-navic-cue-hold-ring-visible': 'false',
    opacity: '0',
  })
  const spin = document.createElementNS(ReaderSvgNamespace, 'animateTransform')
  spin.setAttribute('attributeName', 'transform')
  spin.setAttribute('type', 'rotate')
  spin.setAttribute('from', `0 ${centerX} ${centerY}`)
  spin.setAttribute('to', `360 ${centerX} ${centerY}`)
  spin.setAttribute('dur', '0.8s')
  spin.setAttribute('repeatCount', 'indefinite')
  pendingRing.append(spin)

  const label = document.createElementNS(ReaderSvgNamespace, 'text')
  label.setAttribute('x', centerX)
  label.setAttribute('y', centerY)
  label.setAttribute('fill', options.labelColor || '#fff')
  label.setAttribute('font-size', '7')
  label.setAttribute('font-family', 'sans-serif')
  label.setAttribute('font-weight', '700')
  label.setAttribute('text-anchor', 'middle')
  label.setAttribute('dominant-baseline', 'central')
  label.setAttribute('pointer-events', 'none')
  label.textContent = String(sourceOrdinal)
  group.append(label)
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
