const renderedDestinationCommitOwners = new WeakMap()
const textPageVisibleContentOwners = new WeakMap()
const renderedDestinationCommitStatuses = new Set([
  'committed',
  'mismatch',
  'invalidated',
  'cancelled',
  'unsupported',
])

const isNonNegativeInteger = value => Number.isInteger(value) && value >= 0

const renderedDestinationPositionHasValidShape = position =>
  Object.isFrozen(position) &&
  isNonNegativeInteger(position?.index) &&
  isNonNegativeInteger(position?.pageIndex) &&
  Number.isInteger(position?.pageCount) &&
  position.pageCount > 0 &&
  position.pageIndex < position.pageCount

const renderedDestinationReceiptHasValidShape = receipt =>
  Object.isFrozen(receipt) &&
  isNonNegativeInteger(receipt?.layoutGeneration) &&
  isNonNegativeInteger(receipt?.viewGeneration) &&
  isNonNegativeInteger(receipt?.commitSequence) &&
  receipt?.flow === 'paginated' &&
  renderedDestinationPositionHasValidShape(receipt)

const renderedDestinationCommitResultHasValidShape = (result, index, pageIndex) => {
  if (
    !Object.isFrozen(result) ||
    !renderedDestinationCommitStatuses.has(result?.status) ||
    result.requestedIndex !== index ||
    result.requestedPageIndex !== pageIndex ||
    typeof result.reason !== 'string' ||
    !result.reason
  ) return false

  const hasCommittedPosition = result.status === 'committed' || result.status === 'mismatch'
  if (!hasCommittedPosition) return result.position === null && result.receipt === null
  return renderedDestinationPositionHasValidShape(result.position) &&
    renderedDestinationReceiptHasValidShape(result.receipt) &&
    result.position.index === result.receipt.index &&
    result.position.pageIndex === result.receipt.pageIndex &&
    result.position.pageCount === result.receipt.pageCount
}

const unsupportedRenderedDestinationCommitResult = (index, pageIndex) => Object.freeze({
  status: 'unsupported',
  requestedIndex: index,
  requestedPageIndex: pageIndex,
  position: null,
  receipt: null,
  reason: 'receipt-api-unavailable',
})

const commitmentIsCurrent = commitment =>
  commitment?.validate?.(commitment.receipt) === true

const rememberRenderedDestinationCommit = (owner, renderer, receipt, validate) => {
  if (
    !owner ||
    typeof owner !== 'object' ||
    !Object.isFrozen(owner) ||
    !renderedDestinationReceiptHasValidShape(receipt) ||
    typeof validate !== 'function' ||
    validate(receipt) !== true
  ) return false
  textPageVisibleContentOwners.delete(owner)
  renderedDestinationCommitOwners.set(owner, { renderer, receipt, validate })
  return true
}

const copyRenderedDestinationCommit = (source, owner) => {
  if (
    !source ||
    typeof source !== 'object' ||
    !owner ||
    typeof owner !== 'object' ||
    !Object.isFrozen(owner)
  ) return false
  const commitment = renderedDestinationCommitOwners.get(source)
  if (!commitmentIsCurrent(commitment)) return false
  renderedDestinationCommitOwners.set(owner, commitment)
  if (textPageVisibleContentOwners.get(source) === commitment) {
    textPageVisibleContentOwners.set(owner, commitment)
  } else {
    textPageVisibleContentOwners.delete(owner)
  }
  return true
}

const renderedDestinationCommitIdentity = commitment => {
  if (!commitmentIsCurrent(commitment)) return null
  const receipt = commitment.receipt
  return Object.freeze({
    layoutGeneration: receipt.layoutGeneration,
    viewGeneration: receipt.viewGeneration,
    commitSequence: receipt.commitSequence,
    flow: receipt.flow,
    index: receipt.index,
    pageIndex: receipt.pageIndex,
    pageCount: receipt.pageCount,
  })
}

export async function readerCommitRenderedDestination(
  renderer,
  index,
  pageIndex,
  reason = 'navigation'
) {
  if (!isNonNegativeInteger(index) || !isNonNegativeInteger(pageIndex)) {
    throw new TypeError('Rendered-destination section and page coordinates must be non-negative integers')
  }
  const commit = renderer?.commitRenderedDestination ?? renderer?.commitTextPage
  const validate = renderer?.validateRenderedDestinationCommit ?? renderer?.validateTextPageCommit
  if (typeof commit !== 'function' || typeof validate !== 'function') {
    return unsupportedRenderedDestinationCommitResult(index, pageIndex)
  }

  const result = await commit.call(renderer, index, pageIndex, reason)
  if (!renderedDestinationCommitResultHasValidShape(result, index, pageIndex)) {
    throw new TypeError('Paginator returned an invalid rendered-destination commit result')
  }
  return result
}

export function readerRenderedDestinationCommitIsValid(renderer, result) {
  const validate = renderer?.validateRenderedDestinationCommit ?? renderer?.validateTextPageCommit
  if (
    !renderedDestinationCommitResultHasValidShape(
      result,
      result?.requestedIndex,
      result?.requestedPageIndex,
    ) ||
    (result.status !== 'committed' && result.status !== 'mismatch') ||
    typeof validate !== 'function'
  ) return false
  return validate.call(renderer, result.receipt) === true
}

export function readerRenderedDestinationCommitMatches(result, expected) {
  const position = result?.position
  return renderedDestinationPositionHasValidShape(position) &&
    position.index === Number(expected?.index) &&
    position.pageIndex === Number(expected?.pageIndex) &&
    position.pageCount === Number(expected?.pageCount)
}

export function readerRememberRenderedDestinationCommit(owner, renderer, receipt) {
  const validate = renderer?.validateRenderedDestinationCommit ?? renderer?.validateTextPageCommit
  return rememberRenderedDestinationCommit(
    owner,
    renderer,
    receipt,
    candidate => validate?.call(renderer, candidate) === true,
  )
}

export function readerCopyRenderedDestinationCommit(source, owner) {
  return copyRenderedDestinationCommit(source, owner)
}

export function readerRenderedDestinationCommitOwnerIsValid(owner) {
  const commitment = owner && typeof owner === 'object'
    ? renderedDestinationCommitOwners.get(owner)
    : null
  return commitmentIsCurrent(commitment)
}

export function readerRenderedDestinationCommitIdentity(owner) {
  const commitment = owner && typeof owner === 'object'
    ? renderedDestinationCommitOwners.get(owner)
    : null
  return renderedDestinationCommitIdentity(commitment)
}

export function readerRenderedDestinationCommitOwnerWasRemembered(owner) {
  return Boolean(
    owner &&
    typeof owner === 'object' &&
    renderedDestinationCommitOwners.has(owner)
  )
}

export function readerForgetRenderedDestinationCommit(owner) {
  if (!owner || typeof owner !== 'object') return false
  textPageVisibleContentOwners.delete(owner)
  return renderedDestinationCommitOwners.delete(owner)
}

export async function readerCommitTextPage(renderer, index, pageIndex, reason = 'navigation') {
  if (!isNonNegativeInteger(index) || !isNonNegativeInteger(pageIndex)) {
    throw new TypeError('Text-page section and page coordinates must be non-negative integers')
  }
  if (
    typeof renderer?.commitTextPage !== 'function' ||
    typeof renderer?.validateTextPageCommit !== 'function'
  ) return unsupportedRenderedDestinationCommitResult(index, pageIndex)

  const result = await renderer.commitTextPage(index, pageIndex, reason)
  if (!renderedDestinationCommitResultHasValidShape(result, index, pageIndex)) {
    throw new TypeError('Paginator returned an invalid text-page commit result')
  }
  return result
}

export function readerTextPageCommitIsValid(renderer, result) {
  if (
    !renderedDestinationCommitResultHasValidShape(
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
  return readerRenderedDestinationCommitMatches(result, expected)
}

export function readerRememberTextPageCommit(owner, renderer, receipt) {
  return rememberRenderedDestinationCommit(
    owner,
    renderer,
    receipt,
    candidate => renderer?.validateTextPageCommit?.(candidate) === true,
  )
}

export function readerRememberTextPageVisibleContent(owner) {
  const commitment = owner && typeof owner === 'object'
    ? renderedDestinationCommitOwners.get(owner)
    : null
  if (
    !commitmentIsCurrent(commitment) ||
    commitment.renderer?.validateTextPageVisibleContent?.(commitment.receipt) !== true
  ) return false
  textPageVisibleContentOwners.set(owner, commitment)
  return true
}

export function readerCopyTextPageCommit(source, owner) {
  return copyRenderedDestinationCommit(source, owner)
}

export function readerTextPageCommitOwnerIsValid(owner) {
  return readerRenderedDestinationCommitOwnerIsValid(owner)
}

export function readerTextPageCommitOwnerHasExpectedVisibleContent(owner) {
  const commitment = owner && typeof owner === 'object'
    ? textPageVisibleContentOwners.get(owner)
    : null
  return Boolean(
    commitment &&
    renderedDestinationCommitOwners.get(owner) === commitment &&
    commitmentIsCurrent(commitment) &&
    commitment.renderer?.validateTextPageVisibleContent?.(commitment.receipt) === true
  )
}

export function readerTextPageCommitIdentity(owner) {
  const commitment = owner && typeof owner === 'object'
    ? textPageVisibleContentOwners.get(owner)
    : null
  if (
    !commitment ||
    renderedDestinationCommitOwners.get(owner) !== commitment ||
    !commitmentIsCurrent(commitment) ||
    commitment.renderer?.validateTextPageVisibleContent?.(commitment.receipt) !== true
  ) return null
  return renderedDestinationCommitIdentity(commitment)
}

export function readerTextPageCommitOwnerWasRemembered(owner) {
  return readerRenderedDestinationCommitOwnerWasRemembered(owner)
}

export function readerForgetTextPageCommit(owner) {
  return readerForgetRenderedDestinationCommit(owner)
}
