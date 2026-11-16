import { createBoundedOperationRuntime } from './bounded-operation-runtime.js'
import { PassiveRasterFoliateSession } from './passive-raster-foliate-session.js'

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

// Debug-only #58 paper probe: hide page text and page numbers so the host can capture paper alone.
export const paperProbe = hide => {
  const view = document.querySelector('foliate-view')
  if (!view) return null
  const targets = [
    ...(view.renderer?.getContents?.() || []).map(content => content?.doc?.documentElement),
    document.querySelector('[data-navic-page-number-layer="true"]'),
  ].filter(Boolean)
  for (const element of targets) {
    if (hide) element.style.setProperty('visibility', 'hidden', 'important')
    else element.style.removeProperty('visibility')
  }
  const style = getComputedStyle(view)
  return { hidden: targets.length, foreground: style.color, background: style.backgroundColor }
}

const api = Object.freeze({
  ready: true,
  startCapture,
  cancelOperation,
  readOperationResult,
  boundedStatus,
  paperProbe,
})

globalThis.NavicPassiveRasterPrototype = api
