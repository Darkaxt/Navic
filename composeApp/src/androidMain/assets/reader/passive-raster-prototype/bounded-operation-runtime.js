const MaximumOperations = 24
const MaximumCounter = Number.MAX_SAFE_INTEGER

const incrementBounded = value => value >= MaximumCounter ? value : value + 1

const safeFailure = failure => {
  const name = typeof failure?.name === 'string' ? failure.name : 'Error'
  return name === 'TypeError' ? 'invalid-input' : 'runtime-unavailable'
}

export const createBoundedOperationRuntime = () => {
  const operations = new Map()
  let operationSequence = 0
  let operationAttempts = 0
  let operationCompletions = 0
  let operationFailures = 0

  const trimOperations = () => {
    if (operations.size <= MaximumOperations) return
    for (const [operationId, result] of operations) {
      if (result.state === 'pending') continue
      operations.delete(operationId)
      if (operations.size <= MaximumOperations) return
    }
  }

  const start = work => {
    operationSequence = incrementBounded(operationSequence)
    operationAttempts = incrementBounded(operationAttempts)
    const operationId = `operation-${operationSequence}`
    operations.set(operationId, Object.freeze({ state: 'pending' }))
    trimOperations()
    Promise.resolve()
      .then(work)
      .then(value => {
        operationCompletions = incrementBounded(operationCompletions)
        operations.set(operationId, Object.freeze({ state: 'complete', value }))
        trimOperations()
      })
      .catch(failure => {
        operationFailures = incrementBounded(operationFailures)
        operations.set(operationId, Object.freeze({
          state: 'failed',
          failure: safeFailure(failure),
        }))
        trimOperations()
      })
    return Object.freeze({ operationId })
  }

  const read = (operationId, consume = false) => {
    if (typeof operationId !== 'string') return null
    const result = operations.get(operationId) ?? null
    if (consume && result?.state !== 'pending') operations.delete(operationId)
    return result
  }

  const boundedStatus = () => Object.freeze({
    operationAttempts,
    operationCompletions,
    operationFailures,
    pendingOperations: Array.from(operations.values())
      .filter(result => result.state === 'pending')
      .length,
  })

  return Object.freeze({ start, read, boundedStatus })
}
