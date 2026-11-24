import { createBoundedOperationRuntime } from './bounded-operation-runtime.js'
import { PassiveRasterFoliateSession } from './passive-raster-foliate-session.js'
import { stableHash } from '../navic-reader-identity.js'

let session = null
const operations = createBoundedOperationRuntime()

const activeSession = () => {
  session ??= new PassiveRasterFoliateSession()
  return session
}

export const startCapture = input => operations.startExclusive(
  signal => activeSession().commitCapture(input, signal),
)

export const cancelOperation = operationId => operations.cancel(operationId)

export const readOperationResult = (operationId, consume = false) =>
  operations.read(operationId, consume)

export const boundedStatus = () => Object.freeze({
  ...operations.boundedStatus(),
  sessionReady: session != null,
})

// #58 paper/text split: the paper layers (every data-navic-*-layer except page numbers) identify
// the shared paper; hiding the page text and numbers lets the host capture that paper alone.
const paperSplitTextTargets = view => [
  ...(view.renderer?.getContents?.() || []).map(content => content?.doc?.documentElement),
  document.querySelector('[data-navic-page-number-layer="true"]'),
].filter(Boolean)

const paperSplitLayers = () => Array.from(document.querySelectorAll('*')).filter(element =>
  Object.keys(element.dataset || {}).some(name =>
    name.startsWith('navic') && name.endsWith('Layer') && name !== 'navicPageNumberLayer'))

// A split stores one coverage plane, so any picture on the page would lose its colour (#58): the
// soft-colour plates fit the residual check and were stored monochrome. Such pages stay full PNGs.
const paperSplitMediaSelector = 'img, picture, svg, canvas, video, [data-navic-illustration]'

// Foliate's paginated iframe is as wide as the whole section, so a rect inside it says nothing
// about the visible column: map it through the frame's own top-level rect. A frame that cannot
// be measured counts every picture in its section as visible (fail toward colour).
const paperSplitHasVisibleMedia = view =>
  (view.renderer?.getContents?.() || []).some(content => {
    const media = Array.from(content?.doc?.querySelectorAll?.(paperSplitMediaSelector) || [])
    if (media.length === 0) return false
    const frame = content.doc.defaultView?.frameElement?.getBoundingClientRect?.()
    if (!frame || !(frame.width > 0) || !(frame.height > 0)) return true
    return media.some(element => {
      const rect = element.getBoundingClientRect()
      if (!(rect.width > 0) || !(rect.height > 0)) return false
      const left = frame.left + rect.left
      const top = frame.top + rect.top
      return left + rect.width > 0 && top + rect.height > 0 && left < innerWidth && top < innerHeight
    })
  })

export const paperSplitDescribe = () => {
  const view = document.querySelector('foliate-view')
  if (!view || paperSplitTextTargets(view).length === 0) return null
  if (paperSplitHasVisibleMedia(view)) return null
  const style = getComputedStyle(view)
  const identity = [
    innerWidth,
    innerHeight,
    devicePixelRatio,
    style.backgroundColor,
    ...paperSplitLayers().map(layer => layer.outerHTML),
  ].join('|')
  const fingerprint = stableHash(identity).toString(16).padStart(8, '0') +
    stableHash(`paper|${identity}`).toString(16).padStart(8, '0')
  return { fingerprint, foreground: style.color, background: style.backgroundColor }
}

export const paperSplitHideText = hide => {
  const view = document.querySelector('foliate-view')
  const targets = view ? paperSplitTextTargets(view) : []
  for (const element of targets) {
    if (hide) element.style.setProperty('visibility', 'hidden', 'important')
    else element.style.removeProperty('visibility')
  }
  return targets.length
}

const api = Object.freeze({
  ready: true,
  startCapture,
  cancelOperation,
  readOperationResult,
  boundedStatus,
  paperSplitDescribe,
  paperSplitHideText,
})

globalThis.NavicPassiveRasterPrototype = api
