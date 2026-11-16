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

export const paperSplitDescribe = () => {
  const view = document.querySelector('foliate-view')
  if (!view || paperSplitTextTargets(view).length === 0) return null
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
