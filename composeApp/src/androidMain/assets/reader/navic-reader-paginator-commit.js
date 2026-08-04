const textPageCommitOwners = new WeakMap()
const textPageCommitStatuses = new Set([
  'committed',
  'mismatch',
  'invalidated',
  'cancelled',
  'unsupported',
])

const isNonNegativeInteger = value => Number.isInteger(value) && value >= 0

const textPagePositionHasValidShape = position =>
  Object.isFrozen(position) &&
  isNonNegativeInteger(position?.index) &&
  isNonNegativeInteger(position?.pageIndex) &&
  Number.isInteger(position?.pageCount) &&
  position.pageCount > 0 &&
  position.pageIndex < position.pageCount

const textPageReceiptHasValidShape = receipt =>
  Object.isFrozen(receipt) &&
  isNonNegativeInteger(receipt?.layoutGeneration) &&
  isNonNegativeInteger(receipt?.viewGeneration) &&
  isNonNegativeInteger(receipt?.commitSequence) &&
  receipt?.flow === 'paginated' &&
  textPagePositionHasValidShape(receipt)

const textPageCommitResultHasValidShape = (result, index, pageIndex) => {
  if (
    !Object.isFrozen(result) ||
    !textPageCommitStatuses.has(result?.status) ||
    result.requestedIndex !== index ||
    result.requestedPageIndex !== pageIndex ||
    typeof result.reason !== 'string' ||
    !result.reason
  ) return false

  const hasCommittedPosition = result.status === 'committed' || result.status === 'mismatch'
  if (!hasCommittedPosition) return result.position === null && result.receipt === null
  return textPagePositionHasValidShape(result.position) &&
    textPageReceiptHasValidShape(result.receipt) &&
    result.position.index === result.receipt.index &&
    result.position.pageIndex === result.receipt.pageIndex &&
    result.position.pageCount === result.receipt.pageCount
}

const unsupportedTextPageCommitResult = (index, pageIndex) => Object.freeze({
  status: 'unsupported',
  requestedIndex: index,
  requestedPageIndex: pageIndex,
  position: null,
  receipt: null,
  reason: 'receipt-api-unavailable',
})

export async function readerCommitTextPage(renderer, index, pageIndex, reason = 'navigation') {
  if (!isNonNegativeInteger(index) || !isNonNegativeInteger(pageIndex)) {
    throw new TypeError('Text-page section and page coordinates must be non-negative integers')
  }
  if (
    typeof renderer?.commitTextPage !== 'function' ||
    typeof renderer?.validateTextPageCommit !== 'function'
  ) return unsupportedTextPageCommitResult(index, pageIndex)

  const result = await renderer.commitTextPage(index, pageIndex, reason)
  if (!textPageCommitResultHasValidShape(result, index, pageIndex)) {
    throw new TypeError('Paginator returned an invalid text-page commit result')
  }
  return result
}

export function readerTextPageCommitIsValid(renderer, result) {
  if (
    !textPageCommitResultHasValidShape(
      result,
      result?.requestedIndex,
      result?.requestedPageIndex,
    ) ||
    (result.status !== 'committed' && result.status !== 'mismatch') ||
    typeof renderer?.validateTextPageCommit !== 'function'
  ) return false
  return renderer.validateTextPageCommit(result.receipt) === true
}

export function readerTextPageCommitMatches(result, expected) {
  const position = result?.position
  return textPagePositionHasValidShape(position) &&
    position.index === Number(expected?.index) &&
    position.pageIndex === Number(expected?.pageIndex) &&
    position.pageCount === Number(expected?.pageCount)
}

export function readerRememberTextPageCommit(owner, renderer, receipt) {
  if (
    !owner ||
    typeof owner !== 'object' ||
    !Object.isFrozen(owner) ||
    !textPageReceiptHasValidShape(receipt) ||
    typeof renderer?.validateTextPageCommit !== 'function' ||
    renderer.validateTextPageCommit(receipt) !== true
  ) return false
  textPageCommitOwners.set(owner, { renderer, receipt })
  return true
}

export function readerCopyTextPageCommit(source, owner) {
  if (
    !source ||
    typeof source !== 'object' ||
    !owner ||
    typeof owner !== 'object' ||
    !Object.isFrozen(owner)
  ) return false
  const commitment = textPageCommitOwners.get(source)
  if (
    !commitment ||
    commitment.renderer?.validateTextPageCommit?.(commitment.receipt) !== true
  ) return false
  textPageCommitOwners.set(owner, commitment)
  return true
}

export function readerTextPageCommitOwnerIsValid(owner) {
  const commitment = owner && typeof owner === 'object'
    ? textPageCommitOwners.get(owner)
    : null
  return Boolean(
    commitment &&
    commitment.renderer?.validateTextPageCommit?.(commitment.receipt) === true
  )
}

export function readerTextPageCommitOwnerWasRemembered(owner) {
  return Boolean(
    owner &&
    typeof owner === 'object' &&
    textPageCommitOwners.has(owner)
  )
}

export function readerForgetTextPageCommit(owner) {
  return Boolean(owner && typeof owner === 'object' && textPageCommitOwners.delete(owner))
}
