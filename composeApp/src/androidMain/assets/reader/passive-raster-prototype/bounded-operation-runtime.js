const MaximumOperations = 24
const MaximumCounter = Number.MAX_SAFE_INTEGER

const incrementBounded = value => value >= MaximumCounter ? value : value + 1

const safeFailure = failure => {
  const name = typeof failure?.name === 'string' ? failure.name : 'Error'
  return name === 'TypeError' ? 'invalid-input' : 'runtime-unavailable'
}

const abortFailure = () => new DOMException('Operation cancelled', 'AbortError')

export const throwIfOperationAborted = signal => {
  if (!signal?.aborted) return
  throw signal.reason instanceof Error ? signal.reason : abortFailure()
}

export const createBoundedOperationRuntime = () => {
  const operations = new Map()
  let operationSequence = 0
  let operationAttempts = 0
  let operationCompletions = 0
  let operationFailures = 0
  let operationCancellations = 0
  let exclusiveOperationId = null

  const trimOperations = () => {
    if (operations.size <= MaximumOperations) return
    for (const [operationId, operation] of operations) {
      if (operation.result.state === 'pending' || operation.result.state === 'cancelling') continue
      operations.delete(operationId)
      if (operations.size <= MaximumOperations) return
    }
  }

  const finish = (operationId, operation, result) => {
    if (operations.get(operationId) !== operation) return
    operation.result = Object.freeze(result)
    if (exclusiveOperationId === operationId) exclusiveOperationId = null
    trimOperations()
  }

  const startOperation = (work, exclusive) => {
    if (exclusive && exclusiveOperationId != null) return null
    operationSequence = incrementBounded(operationSequence)
    operationAttempts = incrementBounded(operationAttempts)
    const operationId = `operation-${operationSequence}`
    const operation = {
      controller: new AbortController(),
      result: Object.freeze({ state: 'pending' }),
    }
    operations.set(operationId, operation)
    if (exclusive) exclusiveOperationId = operationId
    trimOperations()
    Promise.resolve()
      .then(() => work(operation.controller.signal))
      .then(value => {
        if (operation.controller.signal.aborted) {
          operationCancellations = incrementBounded(operationCancellations)
          finish(operationId, operation, { state: 'cancelled' })
          return
        }
        operationCompletions = incrementBounded(operationCompletions)
        finish(operationId, operation, { state: 'complete', value })
      })
      .catch(failure => {
        if (operation.controller.signal.aborted) {
          operationCancellations = incrementBounded(operationCancellations)
          finish(operationId, operation, { state: 'cancelled' })
          return
        }
        operationFailures = incrementBounded(operationFailures)
        finish(operationId, operation, {
          state: 'failed',
          failure: safeFailure(failure),
        })
      })
    return Object.freeze({ operationId })
  }

  const start = work => startOperation(work, false)
  const startExclusive = work => startOperation(work, true)

  const cancel = operationId => {
    if (typeof operationId !== 'string') return false
    const operation = operations.get(operationId)
    if (!operation || operation.result.state !== 'pending') return false
    operation.result = Object.freeze({ state: 'cancelling' })
    operation.controller.abort(abortFailure())
    return true
  }

  const read = (operationId, consume = false) => {
    if (typeof operationId !== 'string') return null
    const operation = operations.get(operationId)
    const result = operation?.result ?? null
    if (consume && result?.state !== 'pending' && result?.state !== 'cancelling') {
      operations.delete(operationId)
    }
    return result
  }

  const boundedStatus = () => Object.freeze({
    operationAttempts,
    operationCompletions,
    operationFailures,
    operationCancellations,
    pendingOperations: Array.from(operations.values())
      .filter(operation =>
        operation.result.state === 'pending' || operation.result.state === 'cancelling')
      .length,
  })

  return Object.freeze({
    start,
    startExclusive,
    cancel,
    read,
    boundedStatus,
  })
}
