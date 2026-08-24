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

const api = Object.freeze({
  ready: true,
  startCapture,
  cancelOperation,
  readOperationResult,
  boundedStatus,
})

globalThis.NavicPassiveRasterPrototype = api
