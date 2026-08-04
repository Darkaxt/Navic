import {
  CenterTapMovementSlop,
  CenterTapSyntheticClickDedupeMs,
  FixedLayoutSurfaceSwipeThreshold,
  KomikkuNavigationRegionLeft,
  KomikkuNavigationRegionMenu,
  KomikkuNavigationRegionNext,
  KomikkuNavigationRegionPrevious,
  KomikkuNavigationRegionRight,
  ReaderDirectionDefault,
  ReaderDirectionLtr,
  ReaderDirectionRtl,
  ReaderDocumentThemeStyleId,
  ReaderDragAnimationCanvas,
  ReaderDragAnimationNone,
  ReaderFlowPaged,
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderFontSourceNavic,
  ReaderFontSourceSystem,
  ReaderFontSourcePublisher,
  ReaderMediaSyntheticClickSuppressMs,
  ReaderPageBorderOverlayAssets,
  ReaderPageBorderOverlayVariantCount,
  ReaderPageNumberLayerSelector,
  ReaderPageStainOverlayAssets,
  ReaderPageStainOverlayVariantCount,
  ReaderPaperTextureAssets,
  ReaderPaperTextureVariantCount,
  ReaderReflowableReadableUnitsPerSyntheticPage,
  ReaderReflowableStartProgressPageOffsetThreshold,
  ReaderReflowableProgressEpsilon,
  ReaderShellCoverLayerSelector,
  ReaderShellCoverTransitionMs,
  ReaderSurfacePageBorderOverlayLayerSelector,
  ReaderSurfacePageStainOverlayLayerSelector,
  ReaderSurfacePaperTextureLayerSelector,
  ReaderTapZoneDefault,
  ReaderTapZoneDisabled,
  ReaderThemeLight,
  ReaderThemeSepia,
  ScrollEdgeTurnSlop,
  ScrollEdgeTurnSwipeThreshold,
  optionalNumber,
  readerDirectionMode,
  readerEffectiveFontFamily,
  readerFlowMode,
  readerFoliateFlow,
  readerFontSource,
  readerThemeKey,
  readerThemePalette
} from './navic-reader-settings.js'
import {
  readerRoot,
  overlayClass,
  ReaderThemePalettes,
  log,
  logError,
  readerTraceValue,
  readerTrace,
  readerLocationPostKey,
  describeUrl,
  post,
  reportError,
  errorElement,
  closestElement,
  readerMediaSelector,
  readerLinkHasMedia,
  isReaderMediaAnchor,
  isReaderMediaTapTarget,
  readerPointInsideRect,
  readerEventClientPoint,
  readerPointInsideAnchorText,
  readerMediaElementFromCandidate,
  readerImageFromMediaTarget,
  readerMediaTapTargetForEvent,
  readerRectSnapshot,
  readerRootTapPoint,
  markReaderMediaTapHandled,
  readerLastMediaTapRectContainsPoint,
  readerShouldSuppressMediaSyntheticClick,
  markReaderSurfaceTapHandled,
  shouldSuppressReaderSurfaceClick,
  readerAssetUrl,
  ReaderShellCoverProgressThreshold,
  readerTokenText,
  readerSectionTokenText,
  readerSectionLooksLikeCover,
  readerContentDocumentLooksLikeCover,
  suppressReaderEmbeddedCoverPage,
  readerSectionIsReadable,
  readerHrefComparable,
  readerHrefMatches,
  readerHrefMatchesSection,
  stableHash,
  readerPaperTexturePageLocator,
  readerPaperTextureVariantKey,
  readerSurfaceTextureVariantForPage,
  readerPaperTextureVariantForPage,
  readerPageBorderOverlayVariantForPage,
  readerPaperTextureTransform,
  readerPaperTextureCssOffset,
  readerPaperTextureBackgroundPosition,
  readerPageDragPreviewMotion,
  readerPaperTextureDragDirection,
  readerSurfacePaperTextureScrollOffset,
  readerSurfacePaperTextureOpacity,
  readerSurfacePageBorderOverlayOpacity,
  readerPageNumberPageCount,
  readerPageNumberPositionWithPageCount,
  readerPageNumberLabel,
  readerPageNumberBlendMode,
  readerFontFaceCss,
  readerParagraphSpacingEm,
  applyReaderParagraphSpacing,
  readerNormalizeChapterOpeningMargins,
  ensureReaderSurfaceTextureLayer,
  ensureReaderSurfaceBorderOverlayLayer,
  ensureReaderSurfaceStainOverlayLayer,
  ensureReaderPageNumberLayer,
  ensureReaderShellCoverLayer,
  ensureReaderShellCoverImage,
  ensureTapZoneOverlayLayer,
  updateReaderShellCoverLayer,
  updateReaderMovingPageBorderOverlayLayer,
  updateReaderMovingPageStainOverlayLayer,
  updateReaderMovingPageSpreadGutterOverlayLayer,
  updateReaderMovingPageTextureLayer,
  updateReaderSurfaceTextureLayer,
  updateReaderSurfaceBorderOverlayLayer,
  updateReaderSurfaceStainOverlayLayer,
  updateTapZoneOverlayLayer,
  isParagraphCandidate,
  isReaderParagraphBlock,
  classifyReaderParagraphBlocks,
  setStylesImportant,
  readerViewportSize,
  readerAdaptiveFoliatePageBox,
  readerStartLocatorHasPosition,
  flattenReaderNavigationItems,
  readerNavigationItemMatches,
  readerPaginationFingerprint,
  readerBuildPaginationProfile,
  readerPaginationObservedChapterEntries,
  readerPaginationPositionForLocator,
  readerTypographyCss,
  readerParagraphSpacingCss,
  isThemeBackgroundMediaElement,
  readerDocumentThemeCss,
  readerContentCss,
  komikkuTapAction,
  normalizeSearchResult,
  normalizeExcerpt,
  hrefForCfi,
  flattenTocItems,
  tocLabel
} from './navic-reader-helpers.js'
import { readerPageLocatorForVisualIndex } from './navic-reader-page-turn-model.js'
import { readerGoToExactVisualPage } from './navic-reader-page-turn-preview.js'
import {
  readerCopyTextPageCommit,
  readerForgetTextPageCommit,
  readerRememberTextPageCommit,
  readerTextPageCommitIsValid,
  readerTextPageCommitMatches,
  readerTextPageCommitOwnerIsValid,
  readerTextPageCommitOwnerWasRemembered,
} from './navic-reader-paginator-commit.js'
import {
  ReaderPageTurnPresentationScopeLive,
  issueReaderPageTurnPresentationReceipt,
  readerPageTurnPresentationReceiptMatches,
} from './navic-reader-page-turn-presentation.js'

const ViewportScrollStepRatio = 0.75
const ReaderLivePageTurnMaximumPaginationProfileRepairs = 2
const ReaderLivePageTurnMaximumCommitTransactionAttempts = 3

function progressTargetForSections(fraction) {
  const sectionCount = Number(this.view?.book?.sections?.length)
  if (!Number.isFinite(sectionCount) || sectionCount <= 0) return null
  const index = Math.floor(Math.min(1, Math.max(0, fraction)) * sectionCount)
  return Math.min(sectionCount - 1, Math.max(0, index))
}

function fixedLayoutCurrentPageIndex() {
  if (this.view?.isFixedLayout !== true) return null
  try {
    const index = Number(this.view?.renderer?.index)
    return Number.isFinite(index) ? Math.floor(index) : null
  } catch (error) {
    log('fixed-layout-index:unavailable', error?.message || String(error))
    return null
  }
}

function fixedLayoutNavigationBasePageIndex() {
  if (this.view?.isFixedLayout !== true) return null
  const navigationIndex = Number(this.fixedLayoutNavigationPageIndex)
  if (Number.isFinite(navigationIndex)) return Math.floor(navigationIndex)
  const committedPageIndex = Number(this.currentPagePosition?.pageIndex)
  if (Number.isFinite(committedPageIndex)) return Math.floor(committedPageIndex)
  return this.fixedLayoutCurrentPageIndex()
}

function syncFixedLayoutNavigationPageIndex(pagePosition) {
  if (this.view?.isFixedLayout !== true) return
  const pageIndex = Number(pagePosition?.pageIndex)
  if (!Number.isFinite(pageIndex)) return
  const pendingIndex = Number(this.fixedLayoutNavigationPageIndex)
  if (Number.isFinite(pendingIndex)) {
    if (this.fixedLayoutNavigationDirection === 'next' && pageIndex < pendingIndex) return
    if (this.fixedLayoutNavigationDirection === 'previous' && pageIndex > pendingIndex) return
  }
  this.fixedLayoutNavigationPageIndex = Math.floor(pageIndex)
  this.fixedLayoutNavigationDirection = null
}

function fixedLayoutAdjacentPageTarget(direction) {
  if (this.view?.isFixedLayout !== true) return null
  const current = this.fixedLayoutNavigationBasePageIndex()
  const sectionCount = Number(this.view?.book?.sections?.length)
  if (!Number.isFinite(current) || !Number.isFinite(sectionCount) || sectionCount <= 0) return null
  const forward = direction === 'next'
  const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
  const delta = forward === rtl ? -1 : 1
  const target = current + delta
  return target >= 0 && target < sectionCount ? target : null
}

async function goToProgress(progress, reason = 'progress-seek') {
  if (!this.view) return
  const numericProgress = Number(progress)
  const fraction = Number.isFinite(numericProgress)
    ? Math.min(1, Math.max(0, numericProgress))
    : 0
  let controlledRelocationOwner = null
  try {
    log('progress-seek:start', fraction)
    const canUseFraction = typeof this.view?.goToFraction === 'function' &&
      this.view?.book?.splitTOCHref &&
      this.view?.book?.getTOCFragment
    const progressTarget = this.progressTargetForSections(fraction)
    controlledRelocationOwner = this.beginControlledRelocation(reason)
    let committed
    if (canUseFraction) {
      committed = await this.view.goToFraction(fraction)
    } else if (progressTarget != null) {
      log('progress-seek:fallback-section', progressTarget)
      committed = await this.view.goTo(progressTarget)
    } else {
      committed = await this.view.goTo({ fraction })
    }
    if (committed === false) {
      this.cancelControlledRelocation(controlledRelocationOwner)
      return false
    }
    this.scheduleControlledRelocationFallback(reason)
    this.applyReaderViewportLayout(reason)
    requestAnimationFrame(() => {
      this.logContentLayout('progress-seek')
      log('progress-seek:done', fraction)
    })
    return true
  } catch (error) {
    this.cancelControlledRelocation(controlledRelocationOwner)
    reportError(error, 'navigation_failed')
    return false
  }
}

async function goToChapterProgress(
  href,
  progress,
  chapterPageIndex = null,
  chapterPageCount = null,
  reason = 'chapter-progress-seek',
) {
  if (!this.view) return
  const targetHref = String(href || '').trim()
  if (!targetHref) return
  const numericProgress = Number(progress)
  const fraction = Number.isFinite(numericProgress)
    ? Math.min(1, Math.max(0, numericProgress))
    : 0
  const targetPageIndex = Number(chapterPageIndex)
  const targetPageCount = Number(chapterPageCount)
  const hasExactTargetPage =
    Number.isFinite(targetPageIndex) &&
    Number.isFinite(targetPageCount) &&
    targetPageIndex >= 0 &&
    targetPageCount > 1
  const targetFraction = hasExactTargetPage
    ? Math.min(1, Math.max(0, targetPageIndex / (targetPageCount - 1)))
    : fraction
  let controlledRelocationOwner = null
  try {
    log('chapter-progress-seek:start', targetHref, fraction, targetPageIndex, targetPageCount)
    const resolved = await Promise.resolve(
      this.view.resolveNavigation?.(targetHref) ||
      this.view.book?.resolveHref?.(targetHref)
    )
    const index = Number(resolved?.index)
    const targetAnchor = this.reflowableChapterProgressAnchor(targetFraction)
    controlledRelocationOwner = this.beginControlledRelocation(reason)
    let committed
    if (Number.isFinite(index) && this.view.renderer?.goTo) {
      committed = await this.view.renderer.goTo({ index, anchor: targetAnchor })
      if (committed !== false) this.view.history?.pushState?.({
        href: targetHref,
        chapterFraction: targetFraction,
        chapterPageIndex: hasExactTargetPage ? targetPageIndex : undefined,
        chapterPageCount: hasExactTargetPage ? targetPageCount : undefined,
      })
    } else {
      committed = await this.view.goTo(targetHref)
    }
    if (committed === false) {
      this.cancelControlledRelocation(controlledRelocationOwner)
      return false
    }
    this.scheduleControlledRelocationFallback(reason)
    this.applyReaderViewportLayout(reason)
    requestAnimationFrame(() => {
      this.logContentLayout('chapter-progress-seek')
      log('chapter-progress-seek:done', targetHref, targetFraction)
    })
    return true
  } catch (error) {
    this.cancelControlledRelocation(controlledRelocationOwner)
    reportError(error, 'navigation_failed')
    return false
  }
}

function exactPageTurnSettlementIdentityMatches(
  settlement,
  locator,
  foliateSessionId,
  rasterGeneration,
  textureGeneration,
  paginationProfile
) {
  return settlement?.foliateSessionId === foliateSessionId &&
    settlement?.rasterGeneration === rasterGeneration &&
    settlement?.textureGeneration === textureGeneration &&
    settlement?.pageIndex === locator.pageIndex &&
    settlement?.spineIndex === locator.spineIndex &&
    settlement?.chapterPageIndex === locator.chapterPageIndex &&
    settlement?.paginationProfile === paginationProfile
}

function exactPageTurnSettlementMatches(
  settlement,
  locator,
  foliateSessionId,
  rasterGeneration,
  textureGeneration,
  paginationProfile
) {
  return exactPageTurnSettlementIdentityMatches(
    settlement,
    locator,
    foliateSessionId,
    rasterGeneration,
    textureGeneration,
    paginationProfile
  ) && readerTextPageCommitOwnerIsValid(settlement)
}

function rememberCompletedExactPageTurnSettlement(runtime, settlement) {
  const token = settlement?.token
  if (!token) return
  runtime.completedExactPageTurnSettlements.set(token, settlement)
}

function rememberRetiredExactPageTurnSettlement(runtime, settlement) {
  const token = settlement?.token
  if (!token) return
  runtime.retiredExactPageTurnSettlements.set(token, settlement)
}

function clearPageTurnLivePresentationReceipt() {
  const cleared = this.pageTurnLivePresentationReceiptValue != null
  this.pageTurnLivePresentationReceiptValue = null
  return cleared
}

function clearPageTurnLivePresentationTarget() {
  const target = this.pageTurnLivePresentationTargetValue
  const cleared = target != null
  readerForgetTextPageCommit(target)
  this.pageTurnLivePresentationTargetValue = null
  this.clearPageTurnLivePresentationReceipt()
  return cleared
}

function replacePageTurnLivePresentationTarget(target) {
  this.clearPageTurnLivePresentationTarget()
  this.pageTurnLivePresentationTargetValue = Object.freeze(target)
  return this.pageTurnLivePresentationTargetValue
}

function pageTurnLivePresentationReceiptTarget(target) {
  return {
    scope: ReaderPageTurnPresentationScopeLive,
    token: target.token,
    pageIndex: target.pageIndex,
    foliateSessionId: target.foliateSessionId,
    rasterGeneration: target.rasterGeneration,
    textureGeneration: target.textureGeneration,
  }
}

function pageTurnLivePresentationTargetMatchesCurrent(target) {
  if (!target || !readerTextPageCommitOwnerIsValid(target)) return false
  const pagePosition = this.currentPagePosition
  if (
    target.relocationEpoch !== this.relocateSequence ||
    target.foliateSessionId !== this.foliateSessionId ||
    target.paginationProfile !== this.paginationProfile ||
    !Number.isFinite(Number(pagePosition?.pageIndex)) ||
    Math.floor(Number(pagePosition.pageIndex)) !== target.pageIndex ||
    !Number.isFinite(Number(pagePosition?.spineIndex)) ||
    Math.floor(Number(pagePosition.spineIndex)) !== target.spineIndex ||
    !Number.isFinite(Number(pagePosition?.chapterPageIndex)) ||
    Math.floor(Number(pagePosition.chapterPageIndex)) !== target.chapterPageIndex
  ) return false
  const settlement = this.nativePageTurnSettledState?.token === target.token
    ? this.nativePageTurnSettledState
    : this.completedExactPageTurnSettlements.get(target.token)
  return exactPageTurnSettlementMatches(
    settlement,
    target,
    target.foliateSessionId,
    target.rasterGeneration,
    target.textureGeneration,
    target.paginationProfile
  )
}

function issuePageTurnLivePresentationReceipt(target) {
  if (!readerTextPageCommitOwnerIsValid(this.pageTurnLivePresentationTargetValue)) {
    this.clearPageTurnLivePresentationReceipt()
    return null
  }
  const receipt = issueReaderPageTurnPresentationReceipt(
    target,
    this.pageTurnPresentationSequence
  )
  this.pageTurnPresentationSequence = receipt.presentationSequence
  this.pageTurnLivePresentationReceiptValue = receipt
  return receipt
}

function restorePageTurnLivePresentationReceipt() {
  const target = this.pageTurnLivePresentationTargetValue
  if (!this.pageTurnLivePresentationTargetMatchesCurrent(target)) {
    this.clearPageTurnLivePresentationTarget()
    return null
  }
  return this.issuePageTurnLivePresentationReceipt(
    this.pageTurnLivePresentationReceiptTarget(target)
  )
}

function pageTurnLivePresentationReceipt() {
  const target = this.pageTurnLivePresentationTargetValue
  if (this.pageTurnPreviewExposedToken) {
    this.clearPageTurnLivePresentationReceipt()
    return null
  }
  if (!this.pageTurnLivePresentationTargetMatchesCurrent(target)) {
    this.clearPageTurnLivePresentationTarget()
    return null
  }
  const receipt = this.pageTurnLivePresentationReceiptValue
  if (!receipt) return null
  const receiptTarget = this.pageTurnLivePresentationReceiptTarget(target)
  if (!readerPageTurnPresentationReceiptMatches(receipt, receiptTarget)) {
    this.clearPageTurnLivePresentationReceipt()
    return null
  }
  return receipt
}

function exactPageTurnPendingState({
  token,
  foliateSessionId,
  rasterGeneration,
  textureGeneration,
  locator,
  paginationProfile,
  transactionAttempts = 0,
  profileRepairs = 0,
}) {
  return Object.freeze({
    token,
    foliateSessionId,
    rasterGeneration,
    textureGeneration,
    pageIndex: locator.pageIndex,
    spineIndex: locator.spineIndex,
    chapterPageIndex: locator.chapterPageIndex,
    paginationProfile,
    transactionAttempts,
    profileRepairs,
  })
}

function replacePendingExactPageTurnSettlement(pending, replacement) {
  const token = pending?.token
  if (
    !token ||
    this.activeExactPageTurnSettlementToken !== token ||
    this.pendingExactPageTurnSettlements.get(token) !== pending
  ) return null
  readerForgetTextPageCommit(pending)
  this.pendingExactPageTurnSettlements.set(token, replacement)
  return replacement
}

async function commitPendingExactPageTurnSettlement(token) {
  let pending = this.pendingExactPageTurnSettlements.get(token)
  if (!pending || this.activeExactPageTurnSettlementToken !== token) return null
  this.exactPageTurnNavigationToken = token
  this.exactPageTurnNavigationInProgress = true
  try {
    while (
      pending.transactionAttempts < ReaderLivePageTurnMaximumCommitTransactionAttempts
    ) {
      if (
        this.activeExactPageTurnSettlementToken !== token ||
        this.pendingExactPageTurnSettlements.get(token) !== pending
      ) return null
      const transactionProfile = this.paginationProfile
      const locator = readerPageLocatorForVisualIndex(
        transactionProfile,
        pending.pageIndex
      )
      if (!locator) throw new Error(`Visual page ${pending.pageIndex} is unavailable`)
      const currentPending = exactPageTurnPendingState({
        ...pending,
        locator,
        paginationProfile: transactionProfile,
      })
      pending = this.replacePendingExactPageTurnSettlement(pending, currentPending)
      if (!pending) return locator

      const result = await readerGoToExactVisualPage(
        this.view,
        locator,
        'page-turn:exact'
      )
      if (
        this.activeExactPageTurnSettlementToken !== token ||
        this.pendingExactPageTurnSettlements.get(token) !== pending
      ) return locator
      const attemptedPending = exactPageTurnPendingState({
        ...pending,
        locator,
        paginationProfile: pending.paginationProfile,
        transactionAttempts: pending.transactionAttempts + 1,
      })
      pending = this.replacePendingExactPageTurnSettlement(
        pending,
        attemptedPending
      )
      if (!pending) return locator

      if (result.status === 'unsupported') {
        throw new Error('Exact paginated text navigation is unavailable')
      }
      if (result.status === 'cancelled') {
        throw new Error(`Exact position for page ${pending.pageIndex} was cancelled`)
      }
      if (pending.paginationProfile !== this.paginationProfile) continue

      const receiptIsValid = readerTextPageCommitIsValid(
        this.view?.renderer,
        result
      )
      const expectedPosition = {
        index: locator.spineIndex,
        pageIndex: locator.chapterPageIndex,
        pageCount: locator.chapterPageCount,
      }
      if (
        result.status === 'committed' &&
        receiptIsValid &&
        readerTextPageCommitMatches(result, expectedPosition) &&
        readerRememberTextPageCommit(
          pending,
          this.view?.renderer,
          result.receipt
        )
      ) {
        this.view.history?.pushState?.({
          href: locator.href,
          chapterPageIndex: locator.chapterPageIndex,
          chapterPageCount: locator.chapterPageCount,
        })
        const settledSynchronously = this.maybeCompleteNativePageTurnSettlement(
          this.currentPagePosition
        )
        if (settledSynchronously) {
          const synchronousDelivery = this.postCurrentLocationSnapshot(
            'page-turn:exact-synchronous',
            {
              forceDuplicatePost: true,
              preserveCurrentPagePosition: true,
            }
          )
          if (synchronousDelivery?.posted) {
            this.consumeControlledRelocationReason('page-turn:exact-synchronous')
            return locator
          }
        }
        if (!this.scheduleSettledControlledPageTurnRelocation('exact')) {
          this.scheduleControlledRelocationFallback('page-turn:exact')
        }
        return locator
      }

      const actualPosition = receiptIsValid ? result.position : null
      const canRepairProfile =
        pending.profileRepairs < ReaderLivePageTurnMaximumPaginationProfileRepairs &&
        Number(actualPosition?.index) === Number(locator.spineIndex) &&
        Number(actualPosition?.pageCount) !== Number(locator.chapterPageCount)
      if (canRepairProfile) {
        const repairedProfile = this.repairPaginationProfileFromExactPosition?.(
          locator,
          actualPosition,
          { postLocationSnapshot: false }
        )
        if (
          this.activeExactPageTurnSettlementToken !== token ||
          this.pendingExactPageTurnSettlements.get(token) !== pending
        ) return locator
        if (repairedProfile) {
          const repairedPending = exactPageTurnPendingState({
            ...pending,
            locator,
            paginationProfile: repairedProfile,
            profileRepairs: pending.profileRepairs + 1,
          })
          pending = this.replacePendingExactPageTurnSettlement(
            pending,
            repairedPending
          )
          if (!pending) return locator
          this.postCurrentLocationSnapshot('pagination-profile-repaired')
          continue
        }
      }
      if (result.status === 'invalidated') continue
      if (!receiptIsValid) continue
      throw new Error(`Exact position for page ${pending.pageIndex} was not committed`)
    }
    throw new Error(
      `Exact pagination for page ${pending.pageIndex} did not stabilize`
    )
  } finally {
    if (this.exactPageTurnNavigationToken === token) {
      this.exactPageTurnNavigationToken = null
      this.exactPageTurnNavigationInProgress = false
    }
    if (this.liveTextPageCommitRetryRequestedToken === token) {
      this.liveTextPageCommitRetryRequestedToken = null
      this.handleLiveTextPageCommitInvalidation()
    }
  }
}

async function goToVisualPage(command = {}) {
  const pageIndex = command.pageIndex
  const token = typeof command.settleToken === 'string' ? command.settleToken.trim() : ''
  const settleSessionId = typeof command.settleSessionId === 'string'
    ? command.settleSessionId.trim()
    : ''
  const settleRasterGeneration = command.settleRasterGeneration
  const settleTextureGeneration = command.settleTextureGeneration
  if (!Number.isInteger(pageIndex) || pageIndex < 0) {
    throw new TypeError('Visual page index must be a non-negative integer')
  }
  if (!token) throw new TypeError('Visual page settlement token is required')
  if (!settleSessionId) throw new TypeError('Visual page settlement session is required')
  if (settleSessionId !== this.foliateSessionId) {
    throw new TypeError('Visual page settlement session does not match the active runtime')
  }
  if (
    !Number.isInteger(settleRasterGeneration) ||
    settleRasterGeneration < 0
  ) {
    throw new TypeError('Visual page raster generation must be a non-negative integer')
  }
  if (
    !Number.isInteger(settleTextureGeneration) ||
    settleTextureGeneration < 0
  ) {
    throw new TypeError('Visual page texture generation must be a non-negative integer')
  }
  if (!this.view) return null
  const locator = readerPageLocatorForVisualIndex(this.paginationProfile, pageIndex)
  if (!locator) throw new Error(`Visual page ${pageIndex} is unavailable`)
  const existingPending = this.pendingExactPageTurnSettlements.get(token)
  const existingSettlement = this.nativePageTurnSettledState?.token === token
    ? this.nativePageTurnSettledState
    : null
  const completedSettlement = this.completedExactPageTurnSettlements.get(token)
  const retiredSettlement = this.retiredExactPageTurnSettlements.get(token)
  const existing = existingPending ||
    existingSettlement ||
    completedSettlement ||
    retiredSettlement
  if (existing) {
    if (
      !exactPageTurnSettlementIdentityMatches(
        existing,
        locator,
        settleSessionId,
        settleRasterGeneration,
        settleTextureGeneration,
        this.paginationProfile
      )
    ) {
      throw new TypeError('Visual page settlement token cannot be reused')
    }
    return locator
  }
  if (
    (this.activeExactPageTurnSettlementToken &&
      this.activeExactPageTurnSettlementToken !== token) ||
    (this.nativePageTurnSettledState?.token &&
      this.nativePageTurnSettledState.token !== token)
  ) {
    this.cancelPendingExactPageTurnSettlement('superseded')
  }
  this.clearPageTurnPreviewPresentationReceipt()
  this.clearPageTurnLivePresentationTarget()
  const pending = exactPageTurnPendingState({
    token,
    foliateSessionId: settleSessionId,
    rasterGeneration: settleRasterGeneration,
    textureGeneration: settleTextureGeneration,
    locator,
    paginationProfile: this.paginationProfile,
  })
  this.pendingExactPageTurnSettlements.set(token, pending)
  this.activeExactPageTurnSettlementToken = token
  this.nativePageTurnSettledState = null
  this.nativePageTurnSettledToken = null
  this.exactPageTurnNavigationToken = token
  this.exactPageTurnNavigationInProgress = true
  try {
    this.beginControlledRelocation('page-turn:exact')
    this.applyReaderViewportLayout('page-turn:exact', { renderSynchronously: true })
    return await this.commitPendingExactPageTurnSettlement(token)
  } catch (error) {
    const currentPending = this.pendingExactPageTurnSettlements.get(token)
    const ownsPendingExactNavigation =
      this.activeExactPageTurnSettlementToken === token &&
      currentPending != null
    if (
      ownsPendingExactNavigation &&
      this.controlledRelocateReason === 'page-turn:exact'
    ) {
      this.consumeControlledRelocationReason('page-turn:exact-failed')
    }
    if (currentPending) {
      readerForgetTextPageCommit(currentPending)
      this.pendingExactPageTurnSettlements.delete(token)
      rememberRetiredExactPageTurnSettlement(this, currentPending)
    }
    if (this.activeExactPageTurnSettlementToken === token) {
      this.activeExactPageTurnSettlementToken = null
    }
    if (this.exactPageTurnNavigationToken === token) {
      this.exactPageTurnNavigationToken = null
      this.exactPageTurnNavigationInProgress = false
    }
    throw error
  }
}

function activeExactPageTurnSettlement() {
  const token = this.activeExactPageTurnSettlementToken
  return token ? this.pendingExactPageTurnSettlements.get(token) || null : null
}

function maybeCompleteNativePageTurnSettlement(pagePosition = this.currentPagePosition) {
  const pending = this.activeExactPageTurnSettlement()
  if (!pending) return false
  const pageIndex = Number(pagePosition?.pageIndex)
  const spineIndex = Number(pagePosition?.spineIndex)
  const chapterPageIndex = Number(pagePosition?.chapterPageIndex)
  if (
    !readerTextPageCommitOwnerIsValid(pending) ||
    pending.paginationProfile !== this.paginationProfile ||
    !Number.isFinite(pageIndex) ||
    Math.floor(pageIndex) !== pending.pageIndex ||
    !Number.isFinite(spineIndex) ||
    Math.floor(spineIndex) !== pending.spineIndex ||
    !Number.isFinite(chapterPageIndex) ||
    Math.floor(chapterPageIndex) !== pending.chapterPageIndex
  ) {
    log(
      'page-turn:exact-settle-pending',
      `page=${Number.isFinite(pageIndex) ? Math.floor(pageIndex) : 'n/a'}/${pending.pageIndex}`,
      `spine=${Number.isFinite(spineIndex) ? Math.floor(spineIndex) : 'n/a'}/${pending.spineIndex}`,
      `chapter=${Number.isFinite(chapterPageIndex) ? Math.floor(chapterPageIndex) : 'n/a'}/${pending.chapterPageIndex}`,
      `profile=${pending.paginationProfile === this.paginationProfile}`
    )
    readerTrace('page-turn:exact-settle-pending', {
      requestedPageIndex: pending.pageIndex,
      actualPageIndex: Number.isFinite(pageIndex) ? Math.floor(pageIndex) : null,
      requestedSpineIndex: pending.spineIndex,
      actualSpineIndex: Number.isFinite(spineIndex) ? Math.floor(spineIndex) : null,
      requestedChapterPageIndex: pending.chapterPageIndex,
      actualChapterPageIndex: Number.isFinite(chapterPageIndex) ? Math.floor(chapterPageIndex) : null,
      paginationProfileMatches: pending.paginationProfile === this.paginationProfile,
    })
    return false
  }
  const settledPageIndex = Math.floor(pageIndex)
  const settlement = Object.freeze({
    token: pending.token,
    foliateSessionId: pending.foliateSessionId,
    rasterGeneration: pending.rasterGeneration,
    textureGeneration: pending.textureGeneration,
    pageIndex: settledPageIndex,
    spineIndex: pending.spineIndex,
    chapterPageIndex: pending.chapterPageIndex,
    paginationProfile: pending.paginationProfile,
  })
  if (!readerCopyTextPageCommit(pending, settlement)) return false
  this.nativePageTurnSettledState = settlement
  const presentationTarget = this.replacePageTurnLivePresentationTarget({
    scope: ReaderPageTurnPresentationScopeLive,
    token: pending.token,
    pageIndex: settledPageIndex,
    foliateSessionId: pending.foliateSessionId,
    rasterGeneration: pending.rasterGeneration,
    textureGeneration: pending.textureGeneration,
    relocationEpoch: this.relocateSequence,
    spineIndex: pending.spineIndex,
    chapterPageIndex: pending.chapterPageIndex,
    paginationProfile: pending.paginationProfile,
  })
  if (!readerCopyTextPageCommit(settlement, presentationTarget)) {
    this.nativePageTurnSettledState = null
    this.clearPageTurnLivePresentationTarget()
    return false
  }
  if (!this.pageTurnPreviewExposedToken) {
    this.issuePageTurnLivePresentationReceipt(
      this.pageTurnLivePresentationReceiptTarget(presentationTarget)
    )
  } else {
    this.clearPageTurnLivePresentationReceipt()
  }
  this.nativePageTurnSettledToken = pending.token
  readerTrace('page-turn:exact-settled', { pageIndex: settledPageIndex })
  return true
}

function peekNativePageTurnSettlement(pagePosition = this.currentPagePosition) {
  const settlement = this.nativePageTurnSettledState
  if (!settlement) return null
  const pageIndex = Number(pagePosition?.pageIndex)
  const spineIndex = Number(pagePosition?.spineIndex)
  const chapterPageIndex = Number(pagePosition?.chapterPageIndex)
  const receiptIsValid = readerTextPageCommitOwnerIsValid(settlement)
  if (
    !receiptIsValid ||
    settlement.foliateSessionId !== this.foliateSessionId ||
    settlement.paginationProfile !== this.paginationProfile ||
    !Number.isFinite(pageIndex) ||
    Math.floor(pageIndex) !== settlement.pageIndex ||
    !Number.isFinite(spineIndex) ||
    Math.floor(spineIndex) !== settlement.spineIndex ||
    !Number.isFinite(chapterPageIndex) ||
    Math.floor(chapterPageIndex) !== settlement.chapterPageIndex
  ) {
    this.nativePageTurnSettledState = null
    if (this.nativePageTurnSettledToken === settlement.token) {
      this.nativePageTurnSettledToken = null
    }
    this.clearPageTurnLivePresentationTarget()
    if (!receiptIsValid) this.handleLiveTextPageCommitInvalidation()
    return null
  }
  return settlement
}

function consumeNativePageTurnSettlement(token) {
  const normalizedToken = typeof token === 'string' ? token.trim() : ''
  if (!normalizedToken || this.nativePageTurnSettledState?.token !== normalizedToken) return false
  const settlement = this.nativePageTurnSettledState
  if (!readerTextPageCommitOwnerIsValid(settlement)) {
    this.nativePageTurnSettledState = null
    if (this.nativePageTurnSettledToken === normalizedToken) {
      this.nativePageTurnSettledToken = null
    }
    this.clearPageTurnLivePresentationTarget()
    this.handleLiveTextPageCommitInvalidation()
    return false
  }
  const pending = this.pendingExactPageTurnSettlements.get(normalizedToken)
  if (!pending || this.activeExactPageTurnSettlementToken !== normalizedToken) {
    return false
  }
  this.nativePageTurnSettledState = null
  if (this.nativePageTurnSettledToken === normalizedToken) {
    this.nativePageTurnSettledToken = null
  }
  if (this.pendingExactPageTurnSettlements.get(normalizedToken) === pending) {
    this.pendingExactPageTurnSettlements.delete(normalizedToken)
  }
  if (this.activeExactPageTurnSettlementToken === normalizedToken) {
    this.activeExactPageTurnSettlementToken = null
  }
  readerForgetTextPageCommit(pending)
  rememberCompletedExactPageTurnSettlement(this, settlement)
  return true
}

function cancelPendingExactPageTurnSettlement(reason = 'superseded') {
  this.clearPageTurnLivePresentationTarget()
  const pending = this.activeExactPageTurnSettlement()
  const settled = this.nativePageTurnSettledState
  if (!pending && !settled) return false
  const retired = pending || settled
  if (pending) {
    if (this.pendingExactPageTurnSettlements.get(pending.token) === pending) {
      this.pendingExactPageTurnSettlements.delete(pending.token)
    }
    if (this.activeExactPageTurnSettlementToken === pending.token) {
      this.activeExactPageTurnSettlementToken = null
    }
    readerForgetTextPageCommit(pending)
  }
  if (settled) {
    this.nativePageTurnSettledState = null
    if (this.nativePageTurnSettledToken === settled.token) {
      this.nativePageTurnSettledToken = null
    }
    readerForgetTextPageCommit(settled)
  }
  if (this.exactPageTurnNavigationToken === retired.token) {
    this.exactPageTurnNavigationToken = null
    this.exactPageTurnNavigationInProgress = false
  }
  rememberRetiredExactPageTurnSettlement(this, retired)
  readerTrace('page-turn:exact-cancelled', {
    targetPageIndex: retired.pageIndex ?? null,
    reason,
  })
  return true
}

function detachLiveTextPageCommitInvalidationListener() {
  const target = this.liveTextPageCommitInvalidationTarget
  const listener = this.liveTextPageCommitInvalidationListener
  if (target && listener) {
    target.removeEventListener?.('text-page-commit-invalidated', listener)
  }
  this.liveTextPageCommitInvalidationTarget = null
  this.liveTextPageCommitInvalidationListener = null
}

function attachLiveTextPageCommitInvalidationListener() {
  this.detachLiveTextPageCommitInvalidationListener()
  const renderer = this.view?.renderer
  if (typeof renderer?.addEventListener !== 'function') return false
  const listener = () => {
    if (this.liveTextPageCommitInvalidationTarget !== renderer) return
    this.handleLiveTextPageCommitInvalidation()
  }
  renderer.addEventListener('text-page-commit-invalidated', listener)
  this.liveTextPageCommitInvalidationTarget = renderer
  this.liveTextPageCommitInvalidationListener = listener
  return true
}

function handleLiveTextPageCommitInvalidation() {
  const settled = this.nativePageTurnSettledState
  if (settled && !readerTextPageCommitOwnerIsValid(settled)) {
    this.nativePageTurnSettledState = null
    if (this.nativePageTurnSettledToken === settled.token) {
      this.nativePageTurnSettledToken = null
    }
    readerForgetTextPageCommit(settled)
  }
  const liveTarget = this.pageTurnLivePresentationTargetValue
  if (liveTarget && !readerTextPageCommitOwnerIsValid(liveTarget)) {
    this.clearPageTurnLivePresentationTarget()
  }

  const pending = this.activeExactPageTurnSettlement()
  if (
    !pending ||
    readerTextPageCommitOwnerIsValid(pending) ||
    !readerTextPageCommitOwnerWasRemembered(pending)
  ) return false
  if (
    this.exactPageTurnNavigationInProgress &&
    this.exactPageTurnNavigationToken === pending.token
  ) {
    this.liveTextPageCommitRetryRequestedToken = pending.token
    return true
  }
  if (
    pending.transactionAttempts >= ReaderLivePageTurnMaximumCommitTransactionAttempts
  ) {
    readerForgetTextPageCommit(pending)
    this.pendingExactPageTurnSettlements.delete(pending.token)
    if (this.activeExactPageTurnSettlementToken === pending.token) {
      this.activeExactPageTurnSettlementToken = null
    }
    rememberRetiredExactPageTurnSettlement(this, pending)
    readerTrace('page-turn:exact-invalidated-exhausted', {
      transactionAttempts: pending.transactionAttempts,
    })
    return false
  }
  if (this.liveTextPageCommitRetryToken === pending.token) return true
  this.liveTextPageCommitRetryToken = pending.token
  let controlledRelocationOwner = null
  Promise.resolve()
    .then(() => {
      const current = this.pendingExactPageTurnSettlements.get(pending.token)
      if (
        !current ||
        this.activeExactPageTurnSettlementToken !== pending.token ||
        readerTextPageCommitOwnerIsValid(current) ||
        !readerTextPageCommitOwnerWasRemembered(current)
      ) return null
      this.cancelPendingCommittedRelocation()
      if (
        this.activeExactPageTurnSettlementToken !== pending.token ||
        this.pendingExactPageTurnSettlements.get(pending.token) !== current
      ) return null
      controlledRelocationOwner = this.beginControlledRelocation('page-turn:exact')
      return this.commitPendingExactPageTurnSettlement(pending.token)
    })
    .catch(() => {
      const current = this.pendingExactPageTurnSettlements.get(pending.token)
      if (
        current &&
        this.activeExactPageTurnSettlementToken === pending.token
      ) {
        this.cancelControlledRelocation(controlledRelocationOwner)
        readerForgetTextPageCommit(current)
        this.pendingExactPageTurnSettlements.delete(pending.token)
        this.activeExactPageTurnSettlementToken = null
        rememberRetiredExactPageTurnSettlement(this, current)
      }
      readerTrace('page-turn:exact-invalidated-failed', {
        transactionAttempts: current?.transactionAttempts ?? pending.transactionAttempts,
      })
    })
    .finally(() => {
      if (this.liveTextPageCommitRetryToken === pending.token) {
        this.liveTextPageCommitRetryToken = null
      }
    })
  return true
}

function nextPage() {
  return this.turnPage('next')
}

function previousPage() {
  return this.turnPage('previous')
}

function currentLoadedSectionIndex() {
  const contentIndex = Number(this.view?.renderer?.getContents?.()?.[0]?.index)
  if (Number.isFinite(contentIndex)) return Math.floor(contentIndex)
  const detailIndex = Number(this.lastRelocateDetail?.section?.current ?? this.lastRelocateDetail?.index)
  return Number.isFinite(detailIndex) ? Math.floor(detailIndex) : null
}

function adjacentReadableSectionIndex(direction) {
  const sections = Array.from(this.view?.book?.sections || [])
  const current = this.currentLoadedSectionIndex()
  if (!sections.length || current == null) return null
  const step = direction === 'previous' ? -1 : 1
  for (let index = current + step; index >= 0 && index < sections.length; index += step) {
    const section = sections[index]
    if (readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)) return index
  }
  return null
}

function handleDuplicatePageTurnRelocation(_detail, reason) {
  const reasonText = String(reason || '')
  if (!reasonText.startsWith('page-turn:')) return false
  if (this.pageTurnDuplicateFallbackInProgress) return false
  const direction = reasonText.includes(':previous') ? 'previous' : 'next'
  const targetIndex = this.adjacentReadableSectionIndex(direction)
  const currentIndex = this.currentLoadedSectionIndex()
  if (targetIndex == null || targetIndex === currentIndex) return false
  const fallbackReason = `page-turn:${direction}:adjacent`
  log('page-turn:duplicate-adjacent-fallback', direction, `from=${currentIndex ?? 'n/a'}`, `to=${targetIndex}`)
  readerTrace('page-turn:duplicate-adjacent-fallback', {
    direction,
    currentIndex,
    targetIndex,
    reason: reasonText,
  })
  this.pageTurnDuplicateFallbackInProgress = true
  const controlledRelocationOwner = this.beginControlledRelocation(fallbackReason)
  const navigationPromise = this.view?.goTo?.(targetIndex)
  const fallbackPromise = Promise.resolve(navigationPromise)
    .then(committed => {
      if (committed === false) {
        this.cancelControlledRelocation(controlledRelocationOwner)
        return false
      }
      this.scheduleControlledRelocationFallback(fallbackReason)
      return true
    })
    .catch(error => {
      this.cancelControlledRelocation(controlledRelocationOwner)
      reportError(error, 'navigation_failed')
      return false
    })
    .finally(() => {
      this.pageTurnDuplicateFallbackInProgress = false
    })
  this.pageTurnAdjacentFallbackPromise = fallbackPromise
  return true
}

function nativeDragPreviewAtSectionBoundary(renderer, direction) {
  if (!renderer || renderer.scrolled) return false
  const page = Number(renderer.page)
  const pages = Number(renderer.pages)
  const start = Number(renderer.start)
  const end = Number(renderer.end)
  const viewSize = Number(renderer.viewSize)
  if (!Number.isFinite(page) || !Number.isFinite(pages) || pages <= 0) return false
  if (direction === 'previous') {
    return page <= 1 || (Number.isFinite(start) && start <= 2)
  }
  const lastVisualPage = this.reflowableLastVisualRendererPage(renderer)
  return page >= lastVisualPage ||
    (Number.isFinite(end) && Number.isFinite(viewSize) && viewSize - end <= 2)
}

function readerRendererReadyForPageDrag(renderer) {
  if (!renderer || renderer.scrolled || typeof renderer.scrollBy !== 'function') return false
  try {
    const size = Number(renderer.size)
    const viewSize = Number(renderer.viewSize)
    const page = Number(renderer.page)
    const pages = Number(renderer.pages)
    const start = Number(renderer.start)
    const end = Number(renderer.end)
    return Number.isFinite(size) &&
      Number.isFinite(viewSize) &&
      Number.isFinite(page) &&
      Number.isFinite(pages) &&
      Number.isFinite(start) &&
      Number.isFinite(end) &&
      size > 0 &&
      viewSize > 0 &&
      pages > 0
  } catch (error) {
    readerTrace('page-drag-preview:renderer-not-ready', {
      message: error?.message || String(error),
    })
    return false
  }
}

function positiveRect(rect) {
  return rect &&
    Number.isFinite(Number(rect.width)) &&
    Number.isFinite(Number(rect.height)) &&
    Number(rect.width) > 0 &&
    Number(rect.height) > 0
}

function readerReflowablePageTurnReadiness() {
  if (!this.view) return { ready: false, reason: 'missing-view' }
  if (this.view?.isFixedLayout === true) return { ready: true, reason: 'fixed-layout' }
  const renderer = this.view?.renderer
  if (!renderer) return { ready: false, reason: 'missing-renderer' }
  if (typeof renderer.getContents !== 'function') return { ready: false, reason: 'missing-contents-api' }
  if (typeof renderer.getBoundingClientRect !== 'function') return { ready: false, reason: 'missing-renderer-rect' }
  if (typeof this.view.getBoundingClientRect !== 'function') return { ready: false, reason: 'missing-view-rect' }
  try {
    const viewRect = this.view.getBoundingClientRect()
    const rendererRect = renderer.getBoundingClientRect()
    if (!positiveRect(viewRect)) return { ready: false, reason: 'empty-view-rect' }
    if (!positiveRect(rendererRect)) return { ready: false, reason: 'empty-renderer-rect' }

    const contents = renderer.getContents() || []
    const activeContent = contents.find(content => content?.doc)
    const doc = activeContent?.doc
    if (!doc) return { ready: false, reason: 'missing-content-document' }
    if (!doc.defaultView || !doc.documentElement || !doc.body) {
      return { ready: false, reason: 'incomplete-content-document' }
    }
    if (!doc.defaultView.frameElement?.isConnected) {
      return { ready: false, reason: 'detached-content-frame' }
    }

    const size = Number(renderer.size)
    const viewSize = Number(renderer.viewSize)
    if (!Number.isFinite(size) || size <= 0) return { ready: false, reason: 'invalid-renderer-size' }
    if (!Number.isFinite(viewSize) || viewSize <= 0) return { ready: false, reason: 'invalid-renderer-view-size' }
    if (!renderer.scrolled) {
      const pages = Number(renderer.pages)
      const page = Number(renderer.page)
      if (!Number.isFinite(pages) || pages <= 0) return { ready: false, reason: 'invalid-renderer-pages' }
      if (!Number.isFinite(page)) return { ready: false, reason: 'invalid-renderer-page' }
    }
    return { ready: true, reason: 'ready' }
  } catch (error) {
    return {
      ready: false,
      reason: 'readiness-exception',
      message: error?.message || String(error),
    }
  }
}

function readerReflowablePageTurnReady() {
  return this.readerReflowablePageTurnReadiness().ready
}

function clearDeferredReflowablePageTurn() {
  const pending = this.deferredReflowablePageTurn
  if (!pending) return
  this.deferredReflowablePageTurn = null
  pending.cleanup?.()
}

function retryDeferredReflowablePageTurn(direction) {
  this.clearDeferredReflowablePageTurn()
  const token = ++this.deferredReflowablePageTurnToken
  const renderer = this.view?.renderer
  const resizeObserver = typeof ResizeObserver === 'function'
    ? new ResizeObserver(() => attempt())
    : null
  const cleanupCallbacks = []
  const addCleanup = cleanup => cleanupCallbacks.push(cleanup)
  const cleanup = () => {
    while (cleanupCallbacks.length) cleanupCallbacks.pop()?.()
  }
  const attempt = () => {
    requestAnimationFrame(() => {
      if (this.deferredReflowablePageTurn?.token !== token) return
      const readiness = this.readerReflowablePageTurnReadiness()
      if (!readiness.ready) {
        readerTrace('page-turn:deferred-still-not-ready', {
          direction,
          reason: readiness.reason,
          message: readiness.message,
        })
        return
      }
      this.clearDeferredReflowablePageTurn()
      void this.startPageTurn(direction)
    })
  }

  this.deferredReflowablePageTurn = { direction, token, cleanup }
  if (resizeObserver) {
    const observe = target => {
      if (!target) return
      resizeObserver.observe(target)
      addCleanup(() => resizeObserver.unobserve(target))
    }
    observe(this.view)
    observe(renderer)
    addCleanup(() => resizeObserver.disconnect())
  }
  const addEventListenerCleanup = (target, type) => {
    if (!target?.addEventListener) return
    target.addEventListener(type, attempt)
    addCleanup(() => target.removeEventListener(type, attempt))
  }
  addEventListenerCleanup(renderer, 'load')
  addEventListenerCleanup(renderer, 'relocate')
  addEventListenerCleanup(window, 'resize')
  addEventListenerCleanup(window.visualViewport, 'resize')
  attempt()
}

function safeNativeDragPreviewAtSectionBoundary(renderer, direction) {
  if (!this.readerRendererReadyForPageDrag(renderer)) return false
  try {
    return this.nativeDragPreviewAtSectionBoundary(renderer, direction)
  } catch (error) {
    readerTrace('page-drag-preview:boundary-probe-skipped', {
      direction,
      message: error?.message || String(error),
    })
    return false
  }
}

function ensurePageDragPreviewLayer({ curlEnabled = false } = {}) {
  let layer = this.pageDragPreviewLayer
  if (!layer || !readerRoot.contains(layer)) {
    layer = document.createElement('div')
    layer.dataset.navicPageDragPreviewLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    this.pageDragPreviewLayer = layer
    readerRoot.append(layer)
  }
  const ensureSheet = role => {
    let sheet = layer.querySelector(`[data-navic-page-curl-sheet="${role}"]`)
    if (!sheet) {
      sheet = document.createElement('div')
      sheet.dataset.navicPageCurlSheet = role
      sheet.setAttribute('aria-hidden', 'true')
      sheet.style.pointerEvents = 'none'
      layer.append(sheet)
    }
    return sheet
  }
  const underneath = ensureSheet('underneath')
  if (curlEnabled) {
    const turningFront = ensureSheet('turning-front')
    const turningBack = ensureSheet('turning-back')
    ensureSheet('cast-shadow')
    const ensureSnapshot = (sheet, role) => {
      let snapshot = sheet.querySelector(`[data-navic-page-curl-snapshot="${role}"]`)
      if (!snapshot) {
        snapshot = document.createElement('iframe')
        snapshot.dataset.navicPageCurlSnapshot = role
        snapshot.dataset.navicPageCurlSnapshotReady = 'false'
        snapshot.setAttribute('aria-hidden', 'true')
        snapshot.setAttribute('tabindex', '-1')
        snapshot.style.pointerEvents = 'none'
        sheet.append(snapshot)
      }
      return snapshot
    }
    ensureSnapshot(turningFront, 'front')
    ensureSnapshot(turningBack, 'back')
  } else {
    clearPageDragCurlState(layer)
  }
  let frame = this.pageDragPreviewFrame
  if (!frame || !underneath.contains(frame)) {
    frame = document.createElement('iframe')
    frame.dataset.navicPageDragPreviewFrame = 'true'
    frame.setAttribute('aria-hidden', 'true')
    frame.setAttribute('tabindex', '-1')
    underneath.replaceChildren(frame)
    this.pageDragPreviewFrame = frame
    this.pageDragPreviewTargetKey = ''
  }
  return { layer, frame }
}

function ensurePageDragPreviewLayerChild(layer, attributeName) {
  if (!layer) return null
  let child = layer.querySelector(`[${attributeName}="true"]`)
  if (!child) {
    child = document.createElement('div')
    child.setAttribute(attributeName, 'true')
    child.setAttribute('aria-hidden', 'true')
    child.style.pointerEvents = 'none'
    layer.append(child)
  }
  return child
}

function ensurePageDragPreviewTextureLayers(layer) {
  return {
    paperLayer: ensurePageDragPreviewLayerChild(layer, 'data-navic-page-drag-preview-paper-layer'),
    borderLayer: ensurePageDragPreviewLayerChild(layer, 'data-navic-page-drag-preview-border-layer'),
    gutterLayer: ensurePageDragPreviewLayerChild(layer, 'data-navic-page-drag-preview-gutter-layer'),
    stainLayer: ensurePageDragPreviewLayerChild(layer, 'data-navic-page-drag-preview-stain-layer'),
  }
}

function overridePageDragPreviewTextureLayerBox(layer, zIndex) {
  if (!layer) return
  setStylesImportant(layer, {
    position: 'absolute',
    inset: '0px',
    width: '100%',
    'min-width': '100%',
    height: '100%',
    'min-height': '100%',
    'z-index': String(zIndex),
    overflow: 'hidden',
    'pointer-events': 'none',
  })
}

function syncMovingPageTextureSurface(reason = 'page-drag-preview') {
  if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant && !this.surfaceStainOverlayVariant && !this.surfaceSpreadGutterOverlayVariant) return null
  this.renderSurfacePaperTextureLayers()
  const offset = this.surfaceTextureScrollOffset || { x: 0, y: 0 }
  readerTrace('texture:moving-page-surface', {
    reason,
    offset,
    position: this.currentRendererContainerPosition?.() ?? null,
    baseOffset: this.surfacePaperTextureBaseOffset,
    pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection || '',
  })
  return offset
}

function syncPageDragPreviewTextureLayers(layer, previewScrollOffset = null) {
  if (!layer) return null
  const textureSlots = this.surfaceTextureSlots || []
  const borderOverlaySlots = this.surfaceBorderOverlaySlots || []
  const spreadGutterOverlaySlots = this.surfaceSpreadGutterOverlaySlots || []
  const stainOverlaySlots = this.surfaceStainOverlaySlots || []
  const hasPaper = textureSlots.some(slot => slot?.variant?.asset)
  const hasBorder = borderOverlaySlots.some(slot => slot?.variant?.asset)
  const hasGutter = spreadGutterOverlaySlots.some(slot => slot?.variant?.asset)
  const hasStain = stainOverlaySlots.some(slot => slot?.variant?.asset)
  if (!hasPaper && !hasBorder && !hasGutter && !hasStain) {
    layer.querySelector('[data-navic-page-drag-preview-paper-layer="true"]')?.remove()
    layer.querySelector('[data-navic-page-drag-preview-border-layer="true"]')?.remove()
    layer.querySelector('[data-navic-page-drag-preview-gutter-layer="true"]')?.remove()
    layer.querySelector('[data-navic-page-drag-preview-stain-layer="true"]')?.remove()
    return null
  }
  const { paperLayer, borderLayer, gutterLayer, stainLayer } = this.ensurePageDragPreviewTextureLayers(layer)
  const scrollOffset = previewScrollOffset || this.surfaceTextureScrollOffset || { x: 0, y: 0 }
  const readerDirection = this.effectiveReaderDirection?.() || this.readerDirectionModeValue
  if (hasPaper && paperLayer) {
    updateReaderMovingPageTextureLayer(
      paperLayer,
      textureSlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      this.surfacePageDecorationGeometry
    )
    this.overridePageDragPreviewTextureLayerBox(paperLayer, 2)
  } else {
    paperLayer?.remove()
  }
  if (hasBorder && borderLayer) {
    updateReaderMovingPageBorderOverlayLayer(
      borderLayer,
      borderOverlaySlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      this.surfacePageDecorationGeometry
    )
    this.overridePageDragPreviewTextureLayerBox(borderLayer, 3)
  } else {
    borderLayer?.remove()
  }
  if (hasGutter && gutterLayer) {
    updateReaderMovingPageSpreadGutterOverlayLayer(
      gutterLayer,
      spreadGutterOverlaySlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      this.surfacePageDecorationGeometry
    )
    this.overridePageDragPreviewTextureLayerBox(gutterLayer, 4)
  } else {
    gutterLayer?.remove()
  }
  if (hasStain && stainLayer) {
    updateReaderMovingPageStainOverlayLayer(
      stainLayer,
      stainOverlaySlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection
    )
    this.overridePageDragPreviewTextureLayerBox(stainLayer, 5)
  } else {
    stainLayer?.remove()
  }
  layer.dataset.navicPageDragPreviewTextureSurface = [
    hasPaper ? 'paper' : '',
    hasBorder ? 'border' : '',
    hasGutter ? 'gutter' : '',
    hasStain ? 'stain' : '',
  ].filter(Boolean).join(',')
  return { hasPaper, hasBorder, hasGutter, hasStain }
}

function pageDragPreviewTextureScrollOffset({
  direction,
  frameLeft = 0,
  frameTop = 0,
  width = 0,
  height = 0,
  vertical = false,
  readerDirection = '',
} = {}) {
  const targetSlot = direction === 'previous' ? 'previous' : 'next'
  const horizontal = !vertical
  const axisWidth = Math.round(Number(width) || 0)
  const axisHeight = Math.round(Number(height) || 0)
  const nextSign = horizontal && readerDirection === ReaderDirectionRtl ? -1 : 1
  const slotSign = targetSlot === 'next' ? nextSign : -nextSign
  const slotBaseX = horizontal ? axisWidth * slotSign : 0
  const slotBaseY = horizontal ? 0 : axisHeight * slotSign
  return {
    x: horizontal ? Math.round(Number(frameLeft) || 0) - slotBaseX : 0,
    y: horizontal ? 0 : Math.round(Number(frameTop) || 0) - slotBaseY,
  }
}

function removePageDragPreviewLayer() {
  readerTrace('page-drag-preview:removed', {
    hadLayer: Boolean(this.pageDragPreviewLayer),
    targetKey: this.pageDragPreviewTargetKey || '',
    readyKey: this.pageDragPreviewReadyKey || '',
  })
  this.pageDragPreviewLoadToken += 1
  this.pageDragPreviewLayer?.remove?.()
  this.pageDragPreviewLayer = null
  this.pageDragPreviewFrame = null
  this.pageDragPreviewTargetKey = ''
  this.pageDragPreviewReadyKey = ''
}

function pageDragPreviewDimensions(viewWidth = null, viewHeight = null) {
  const viewport = readerViewportSize()
  return {
    width: Math.max(1, Math.round(Number(viewWidth) || viewport.width || window.innerWidth || 1)),
    height: Math.max(1, Math.round(Number(viewHeight) || viewport.height || window.innerHeight || 1)),
  }
}

function readerPageDragCurlMetrics({ direction, deltaX, deltaY, width, height, vertical }) {
  const axisDistance = Math.abs(Number(vertical ? deltaY : deltaX) || 0)
  const axisSize = Math.max(1, Number(vertical ? height : width) || 1)
  const progress = Math.max(0, Math.min(1, axisDistance / axisSize))
  const eased = progress < 0.5
    ? 2 * progress * progress
    : 1 - Math.pow(-2 * progress + 2, 2) / 2
  const sign = direction === 'previous' ? 1 : -1
  const angleLimit = vertical ? 72 : 88
  const angle = sign * angleLimit * eased
  const frontShadow = 0.10 + Math.sin(Math.PI * progress) * 0.30
  const spineShadow = 0.04 + progress * 0.08
  const shadowAlpha = Math.min(0.34, 0.08 + Math.sin(Math.PI * progress) * 0.22)
  const curlWidth = 16 + Math.sin(Math.PI * progress) * 36
  return { progress, eased, angle, frontShadow, spineShadow, shadowAlpha, curlWidth }
}

function readerDragAnimationModeAllowsCurl(mode) {
  return false
}

function readerNativeDragSnapVelocity({ direction, flowMode, readerDirection } = {}) {
  if (flowMode === ReaderFlowPagedVertical) {
    return {
      vx: 0,
      vy: direction === 'previous' ? -1 : 1,
    }
  }
  const rtl = readerDirection === ReaderDirectionRtl
  return {
    vx: direction === 'previous'
      ? (rtl ? 1 : -1)
      : (rtl ? -1 : 1),
    vy: 0,
  }
}

function clearPageDragCurlState(layer) {
  if (!layer) return
  layer.dataset.navicPageDragPreviewCurl = 'false'
  layer.setAttribute('data-navic-page-drag-preview-curl', 'false')
  layer.dataset.navicPageDragPreviewCurlProgress = '0'
  layer.dataset.navicPageDragPreviewCurlDirection = ''
  layer.dataset.navicPageCurlSheetMode = ''
  layer.dataset.navicPageCurlSheetRoles = ''
  layer.dataset.navicPageCurlSnapshots = ''
  layer.dataset.navicPageCurlSnapshotFront = 'false'
  layer.dataset.navicPageCurlSnapshotBack = 'false'
  layer.style.removeProperty('--navic-page-curl-progress')
  layer.style.removeProperty('--navic-page-curl-eased')
  layer.style.removeProperty('--navic-page-curl-angle')
  layer.style.removeProperty('--navic-page-curl-width')
  layer.style.removeProperty('--navic-page-curl-front-shadow')
  layer.style.removeProperty('--navic-page-curl-spine-shadow')
  layer.style.removeProperty('--navic-page-curl-shadow-alpha')
  layer.style.removeProperty('--navic-page-curl-origin')
  layer.style.removeProperty('--navic-page-curl-transform')
  layer.style.removeProperty('--navic-page-curl-front-face-opacity')
  layer.style.removeProperty('--navic-page-curl-back-face-opacity')
  layer.style.removeProperty('--navic-page-curl-sheet-width')
  layer.style.removeProperty('--navic-page-curl-sheet-height')
  layer.querySelectorAll?.('[data-navic-page-curl-sheet]')?.forEach(element => {
    if (element?.dataset?.navicPageCurlSheet === 'underneath') return
    element.remove()
  })
  layer.querySelectorAll?.('[data-navic-page-curl-snapshot]')?.forEach(element => element.remove())
}

function applyPageDragCurlMetrics(layer, { direction, deltaX, deltaY, width, height, vertical }) {
  if (!layer) return null
  const metrics = readerPageDragCurlMetrics({ direction, deltaX, deltaY, width, height, vertical })
  layer.dataset.navicPageDragPreviewCurl = 'true'
  layer.setAttribute('data-navic-page-drag-preview-curl', 'true')
  layer.dataset.navicPageDragPreviewCurlProgress = metrics.progress.toFixed(3)
  layer.dataset.navicPageDragPreviewCurlDirection = direction || ''
  const origin = vertical
    ? (direction === 'next' ? 'center bottom' : 'center top')
    : (direction === 'next' ? 'left center' : 'right center')
  const transform = vertical
    ? `perspective(1800px) rotateX(${metrics.angle.toFixed(2)}deg)`
    : `perspective(1800px) rotateY(${metrics.angle.toFixed(2)}deg)`
  layer.style.setProperty('--navic-page-curl-progress', metrics.progress.toFixed(3))
  layer.style.setProperty('--navic-page-curl-eased', metrics.eased.toFixed(3))
  layer.style.setProperty('--navic-page-curl-angle', `${metrics.angle.toFixed(2)}deg`)
  layer.style.setProperty('--navic-page-curl-width', `${metrics.curlWidth.toFixed(1)}px`)
  layer.style.setProperty('--navic-page-curl-front-shadow', metrics.frontShadow.toFixed(3))
  layer.style.setProperty('--navic-page-curl-spine-shadow', metrics.spineShadow.toFixed(3))
  layer.style.setProperty('--navic-page-curl-shadow-alpha', metrics.shadowAlpha.toFixed(3))
  layer.style.setProperty('--navic-page-curl-origin', origin)
  layer.style.setProperty('--navic-page-curl-transform', transform)
  return metrics
}

function applyPageDragCurlSheet(layer, { direction, width, height, vertical, palette }) {
  if (!layer) return null
  const mode = vertical || width < height * 1.12 ? 'single' : 'spread'
  const progress = Math.max(0, Math.min(1, Number(layer.style.getPropertyValue('--navic-page-curl-progress')) || 0))
  const frontFaceOpacity = mode === 'single'
    ? (progress < 0.78 ? 1 : progress > 0.98 ? 0 : 1 - ((progress - 0.78) / 0.20))
    : (progress < 0.46 ? 1 : progress > 0.52 ? 0 : 1 - ((progress - 0.46) / 0.06))
  const backFaceOpacity = mode === 'single'
    ? 0
    : (progress < 0.50 ? 0 : progress > 0.56 ? 1 : ((progress - 0.50) / 0.06))
  const roles = ['underneath', 'turning-front', 'turning-back', 'cast-shadow']
  const children = Object.fromEntries(roles.map(role => [
    role,
    layer.querySelector(`[data-navic-page-curl-sheet="${role}"]`),
  ]))
  layer.dataset.navicPageCurlSheetMode = mode
  layer.dataset.navicPageCurlSheetRoles = roles.filter(role => children[role]).join(',')
  layer.style.setProperty('--navic-page-curl-front-face-opacity', Math.max(0, Math.min(1, frontFaceOpacity)).toFixed(3))
  layer.style.setProperty('--navic-page-curl-back-face-opacity', Math.max(0, Math.min(1, backFaceOpacity)).toFixed(3))
  layer.style.setProperty('--navic-page-curl-sheet-width', `${Math.max(1, Math.round(width || 1))}px`)
  layer.style.setProperty('--navic-page-curl-sheet-height', `${Math.max(1, Math.round(height || 1))}px`)
  const axisGradient = vertical
    ? 'linear-gradient(180deg, rgba(20,11,3,var(--navic-page-curl-spine-shadow)) 0, transparent 18%, transparent 70%, rgba(255,255,255,.30) 82%, rgba(30,15,4,var(--navic-page-curl-front-shadow)) 100%)'
    : 'linear-gradient(90deg, rgba(20,11,3,var(--navic-page-curl-spine-shadow)) 0, transparent 18%, transparent 70%, rgba(255,255,255,.30) 82%, rgba(30,15,4,var(--navic-page-curl-front-shadow)) 100%)'
  const reverseGradient = vertical
    ? 'linear-gradient(180deg, rgba(38,20,6,.24) 0, rgba(255,255,255,.18) 24%, transparent 58%, rgba(36,18,5,.16) 100%)'
    : 'linear-gradient(90deg, rgba(38,20,6,.24) 0, rgba(255,255,255,.18) 24%, transparent 58%, rgba(36,18,5,.16) 100%)'
  const sheetBase = {
    position: 'absolute',
    inset: '0',
    width: '100%',
    height: '100%',
    overflow: 'hidden',
    'pointer-events': 'none',
    'box-sizing': 'border-box',
  }
  if (children.underneath) {
    setStylesImportant(children.underneath, {
      ...sheetBase,
      'z-index': '1',
      background: palette?.background || 'transparent',
      'background-color': palette?.background || 'transparent',
    })
  }
  if (children['cast-shadow']) {
    setStylesImportant(children['cast-shadow'], {
      ...sheetBase,
      'z-index': '2',
      opacity: 'var(--navic-page-curl-progress)',
      background: vertical
        ? 'linear-gradient(180deg, rgba(0,0,0,var(--navic-page-curl-shadow-alpha)), transparent 72%)'
        : 'linear-gradient(90deg, rgba(0,0,0,var(--navic-page-curl-shadow-alpha)), transparent 72%)',
      'mix-blend-mode': 'multiply',
    })
  }
  if (children['turning-front']) {
    setStylesImportant(children['turning-front'], {
      ...sheetBase,
      'z-index': '3',
      opacity: 'var(--navic-page-curl-front-face-opacity)',
      background: axisGradient,
      'box-shadow': '0 0 var(--navic-page-curl-width) rgba(0,0,0,var(--navic-page-curl-shadow-alpha))',
      transform: 'var(--navic-page-curl-transform)',
      'transform-origin': 'var(--navic-page-curl-origin)',
      'backface-visibility': 'hidden',
      'will-change': 'transform, opacity',
    })
  }
  if (children['turning-back']) {
    setStylesImportant(children['turning-back'], {
      ...sheetBase,
      'z-index': '4',
      opacity: 'var(--navic-page-curl-back-face-opacity)',
      background: reverseGradient,
      transform: vertical
        ? 'var(--navic-page-curl-transform) rotateX(180deg)'
        : 'var(--navic-page-curl-transform) rotateY(180deg)',
      'transform-origin': 'var(--navic-page-curl-origin)',
      'backface-visibility': 'hidden',
      'will-change': 'transform, opacity',
    })
  }
  return { mode, roles }
}

function pageDragCurlSnapshotScroll(doc) {
  const win = doc?.defaultView
  const root = doc?.documentElement
  const body = doc?.body
  return {
    x: Math.max(0, Math.round(Number(win?.scrollX ?? root?.scrollLeft ?? body?.scrollLeft) || 0)),
    y: Math.max(0, Math.round(Number(win?.scrollY ?? root?.scrollTop ?? body?.scrollTop) || 0)),
  }
}

function pageDragCurlSnapshotHtml(doc, layout = null) {
  const sourceRoot = doc?.documentElement
  if (!sourceRoot) return ''
  const clone = sourceRoot.cloneNode(true)
  for (const script of Array.from(clone.querySelectorAll?.('script') || [])) {
    script.remove()
  }
  let head = clone.querySelector?.('head')
  if (!head) {
    head = doc.createElement('head')
    clone.insertBefore(head, clone.firstChild)
  }
  let body = clone.querySelector?.('body')
  if (!body) {
    body = doc.createElement('body')
    clone.append(body)
  }
  clone.removeAttribute('style')
  body.removeAttribute('style')
  const flow = doc.createElement('div')
  flow.setAttribute('data-navic-page-curl-snapshot-flow', 'true')
  while (body.firstChild) {
    flow.append(body.firstChild)
  }
  body.append(flow)
  const style = doc.createElement('style')
  style.setAttribute('data-navic-page-curl-snapshot-style', 'true')
  const layoutWidth = Math.max(0, Math.round(Number(layout?.width) || 0))
  const layoutViewSize = Math.max(0, Math.round(Number(layout?.viewSize) || 0))
  const layoutAxisStep = Math.max(0, Math.round(Number(layout?.axisStep) || 0))
  const layoutHeight = Math.max(0, Math.round(Number(layout?.height) || 0))
  const sourceStyle = doc.defaultView?.getComputedStyle?.(sourceRoot) || null
  const sourceColumnGap = sourceStyle?.columnGap && sourceStyle.columnGap !== 'normal'
    ? sourceStyle.columnGap
    : '0px'
  const sourceColumnWidth = sourceStyle?.columnWidth && sourceStyle.columnWidth !== 'auto'
    ? sourceStyle.columnWidth
    : `${layoutAxisStep}px`
  const sourcePadding = sourceStyle
    ? `${sourceStyle.paddingTop || '0px'} ${sourceStyle.paddingRight || '0px'} ${sourceStyle.paddingBottom || '0px'} ${sourceStyle.paddingLeft || '0px'}`
    : '0px'
  const pagedLayoutCss = layoutViewSize > 0 && layoutAxisStep > 0 && layoutHeight > 0
    ? [
        `width:${layoutViewSize}px!important;`,
        `min-width:${layoutViewSize}px!important;`,
        `max-width:${layoutViewSize}px!important;`,
        `height:${layoutHeight}px!important;`,
        `min-height:${layoutHeight}px!important;`,
        `column-width:${sourceColumnWidth}!important;`,
        `column-gap:${sourceColumnGap}!important;`,
        'column-fill:auto!important;',
        `padding:${sourcePadding}!important;`,
        'overflow-wrap:break-word!important;',
        'position:static!important;',
        'border:0!important;',
        'margin:0!important;',
        'max-height:none!important;',
        'max-width:none!important;',
        'min-height:none!important;',
        'min-width:none!important;',
      ].join('')
    : ''
  const viewportSizeCss = layoutWidth > 0 && layoutHeight > 0
    ? [
        `width:${layoutWidth}px!important;`,
        `min-width:${layoutWidth}px!important;`,
        `max-width:${layoutWidth}px!important;`,
        `height:${layoutHeight}px!important;`,
        `min-height:${layoutHeight}px!important;`,
        `max-height:${layoutHeight}px!important;`,
      ].join('')
    : ''
  style.textContent = [
    'html{',
    'margin:0!important;',
    'box-sizing:border-box!important;',
    'background-color:var(--reader-background, transparent)!important;',
    'pointer-events:none!important;',
    'overflow:hidden!important;',
    'column-width:auto!important;',
    'column-gap:normal!important;',
    'column-fill:balance!important;',
    viewportSizeCss,
    '}',
    'body{',
    'margin:0!important;',
    'padding:0!important;',
    'box-sizing:border-box!important;',
    viewportSizeCss,
    'background-color:var(--reader-background, transparent)!important;',
    'pointer-events:none!important;',
    'overflow:hidden!important;',
    'column-width:auto!important;',
    'column-gap:normal!important;',
    'column-fill:balance!important;',
    'position:relative!important;',
    'transform-origin:0 0!important;',
    'will-change:transform!important;',
    'max-height:none!important;',
    'max-width:none!important;',
    '}',
    '[data-navic-page-curl-snapshot-flow="true"]{',
    'display:block!important;',
    'box-sizing:border-box!important;',
    pagedLayoutCss || viewportSizeCss,
    'transform-origin:0 0!important;',
    'will-change:transform!important;',
    '}',
    'html::-webkit-scrollbar,body::-webkit-scrollbar{display:none!important;}',
    '*,*::before,*::after{pointer-events:none!important;}',
    'img,svg,canvas,video{max-width:100%;}',
  ].join('')
  head.append(style)
  return `<!doctype html>\n${clone.outerHTML}`
}

function pageDragCurlSnapshotKey({ role, direction, width, height, content, doc, renderer }) {
  return [
    role,
    direction || '',
    Number(content?.index),
    String(doc?.URL || doc?.baseURI || ''),
    Number(renderer?.page),
    Number(renderer?.start),
    Number(renderer?.end),
    `${Math.round(Number(width) || 0)}x${Math.round(Number(height) || 0)}`,
  ].join('|')
}

function syncPageDragCurlSnapshotFrame(snapshot, doc, {
  role,
  direction,
  width,
  height,
  key,
  palette,
  onReady,
  layout = null,
  targetScroll = null,
  renderer = null,
  vertical = false,
}) {
  if (!snapshot || !doc?.documentElement) {
    if (snapshot) {
      snapshot.dataset.navicPageCurlSnapshotReady = 'false'
      snapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    }
    onReady?.()
    return false
  }
  const scroll = targetScroll || pageDragCurlSnapshotScroll(doc)
  setStylesImportant(snapshot, {
    position: 'absolute',
    top: '0px',
    left: '0px',
    width: `${Math.max(1, Math.round(Number(width) || 1))}px`,
    height: `${Math.max(1, Math.round(Number(height) || 1))}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette?.background || 'transparent',
    'background-color': palette?.background || 'transparent',
    color: palette?.foreground || 'inherit',
    'pointer-events': 'none',
  })
  snapshot.dataset.navicPageCurlSnapshotRole = role
  snapshot.dataset.navicPageCurlSnapshotDirection = direction || ''
  snapshot.dataset.navicPageCurlSnapshotScrollX = String(scroll.x)
  snapshot.dataset.navicPageCurlSnapshotScrollY = String(scroll.y)
  const markSnapshotReady = () => {
    try {
      const snapshotDoc = snapshot.contentDocument
      const text = snapshotDoc?.body?.textContent?.replace(/\s+/g, ' ').trim() || ''
      const mappedScroll = targetScroll
        ? pageDragMappedPreviewScroll(snapshot, snapshotDoc, targetScroll, { renderer, vertical })
        : scroll
      snapshot.dataset.navicPageCurlSnapshotMappedScrollX = String(mappedScroll.x)
      snapshot.dataset.navicPageCurlSnapshotMappedScrollY = String(mappedScroll.y)
      applyPageDragPreviewDocumentOffset(snapshot, snapshotDoc, mappedScroll)
      snapshot.dataset.navicPageCurlSnapshotReady = snapshotDoc?.body ? 'true' : 'false'
      snapshot.dataset.navicPageCurlSnapshotTextLength = String(text.length)
    } catch {
      snapshot.dataset.navicPageCurlSnapshotReady = 'false'
      snapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    }
    onReady?.()
    return snapshot.dataset.navicPageCurlSnapshotReady === 'true'
  }
  if (snapshot.dataset.navicPageCurlSnapshotKey !== key) {
    snapshot.dataset.navicPageCurlSnapshotKey = key
    snapshot.dataset.navicPageCurlSnapshotReady = 'false'
    snapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    snapshot.onload = () => { markSnapshotReady() }
    snapshot.removeAttribute('src')
    snapshot.srcdoc = pageDragCurlSnapshotHtml(doc, layout)
    requestAnimationFrame(() => { markSnapshotReady() })
  } else if (snapshot.contentDocument?.body) {
    markSnapshotReady()
  }
  return snapshot.dataset.navicPageCurlSnapshotReady === 'true'
}

function syncPageDragCurlSnapshots(layer, { renderer, frame, ready, mode, direction, width, height, palette }) {
  if (!layer) return null
  const frontSnapshot = layer.querySelector('[data-navic-page-curl-sheet="turning-front"] [data-navic-page-curl-snapshot="front"]')
  const backSnapshot = layer.querySelector('[data-navic-page-curl-sheet="turning-back"] [data-navic-page-curl-snapshot="back"]')
  const recordSnapshotState = () => {
    const frontReady = frontSnapshot?.dataset.navicPageCurlSnapshotReady === 'true'
    const backReady = backSnapshot?.dataset.navicPageCurlSnapshotReady === 'true'
    layer.dataset.navicPageCurlSnapshots = [
      frontReady ? 'front' : '',
      backReady ? 'back' : '',
    ].filter(Boolean).join(',')
    layer.dataset.navicPageCurlSnapshotFront = String(frontReady)
    layer.dataset.navicPageCurlSnapshotBack = String(backReady)
  }
  const contents = typeof renderer?.getContents === 'function' ? (renderer.getContents() || []) : []
  const frontContent = contents.find(content => content?.doc)
  const frontDoc = frontContent?.doc
  const vertical = this.readerFlowModeValue === ReaderFlowPagedVertical
  const frontAxisStep = readerRendererPageStride(renderer, { width, height, vertical })
  const frontLayout = {
    width,
    height,
    axisStep: frontAxisStep,
    viewSize: Number(renderer?.viewSize),
  }
  const frontTargetScroll = pageDragCurrentRendererScroll(frontDoc, { width, height, vertical, renderer })
  const frontReady = this.syncPageDragCurlSnapshotFrame(frontSnapshot, frontDoc, {
    role: 'front',
    direction,
    width,
    height,
    key: pageDragCurlSnapshotKey({ role: 'front', direction, width, height, content: frontContent, doc: frontDoc, renderer }),
    palette,
    layout: frontLayout,
    targetScroll: frontTargetScroll,
    renderer,
    vertical,
    onReady: recordSnapshotState,
  })
  let backReady = false
  if (mode === 'spread' && ready && frame?.contentDocument?.documentElement) {
    const backDoc = frame.contentDocument
    backReady = this.syncPageDragCurlSnapshotFrame(backSnapshot, backDoc, {
      role: 'back',
      direction,
      width,
      height,
      key: pageDragCurlSnapshotKey({ role: 'back', direction, width, height, content: { index: Number(layer.dataset.navicPageDragPreviewTargetIndex) }, doc: backDoc, renderer }),
      palette,
      onReady: recordSnapshotState,
    })
  } else if (backSnapshot) {
    backSnapshot.dataset.navicPageCurlSnapshotReady = 'false'
    backSnapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    setStylesImportant(backSnapshot, {
      opacity: '0',
      'pointer-events': 'none',
    })
  }
  recordSnapshotState()
  return { frontReady, backReady }
}

function buildPageDragPreviewTargetKey(targetIndex, direction, width, height) {
  return `${targetIndex}:${direction}:${width}x${height}`
}

function loadPageDragPreviewFrame(frame, targetIndex, direction, token, targetKey) {
  const section = this.view?.book?.sections?.[targetIndex]
  if (!frame || !section?.load) return
  Promise.resolve(section.load())
    .then(async src => {
      if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return
      await new Promise((resolve, reject) => {
        frame.onload = () => resolve()
        frame.onerror = () => reject(new Error(`Failed to load page drag preview section ${targetIndex}`))
        if (typeof src === 'string' && src.startsWith('blob:')) {
          fetch(src)
            .then(response => response.ok ? response.text() : Promise.reject(new Error(`HTTP ${response.status}`)))
            .then(html => {
              if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return resolve()
              frame.removeAttribute('src')
              frame.srcdoc = html
            })
            .catch(() => {
              if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return resolve()
              frame.removeAttribute('srcdoc')
              frame.src = src
            })
        } else {
          frame.removeAttribute('srcdoc')
          frame.src = src
        }
      })
      if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return
      const doc = frame.contentDocument
      this.applyDocumentDirection(doc, this.readerDirectionModeValue)
      this.applyDocumentTheme(doc, this.readerSettings, targetIndex)
      if (doc?.documentElement) {
        setStylesImportant(doc.documentElement, {
          width: '100%',
          height: '100%',
          'min-height': '100%',
          overflow: 'hidden',
        })
      }
      if (doc?.body) {
        setStylesImportant(doc.body, {
          margin: '0',
          'box-sizing': 'border-box',
          overflow: 'hidden',
        })
      }
      if (direction === 'previous') {
        requestAnimationFrame(() => {
          try {
            const scrollHeight = doc?.documentElement?.scrollHeight || doc?.body?.scrollHeight || 0
            frame.contentWindow?.scrollTo?.(0, scrollHeight)
          } catch {
            // The preview is best-effort and pointer-events disabled; navigation remains authoritative.
          }
        })
      }
      readerTrace('page-drag-preview:underlay-loaded', {
        targetIndex,
        direction,
        href: section?.href || section?.id || '',
      })
      if (targetKey && token === this.pageDragPreviewLoadToken && frame === this.pageDragPreviewFrame) {
        frame.dataset.navicPageDragPreviewLoadedKey = targetKey
        this.pageDragPreviewReadyKey = targetKey
        const pending = this.pendingPageDragPreviewCommand
        readerTrace('page-drag-preview:pending-ready-check', {
          targetKey,
          pendingTargetKey: pending?.targetKey || '',
          readyKey: this.pageDragPreviewReadyKey || '',
        })
        if (pending?.targetKey === targetKey && pending?.command) {
          const command = pending.command
          this.pendingPageDragPreviewCommand = null
          readerTrace('page-drag-preview:pending-replay', {
            targetKey,
            direction: command?.deltaX < 0 ? 'next' : command?.deltaX > 0 ? 'previous' : '',
          })
          this.previewPageDrag(command)
        }
      }
    })
    .catch(error => {
      if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return
      readerTrace('page-drag-preview:underlay-load-failed', {
        targetIndex,
        direction,
        message: error?.message || String(error),
      })
    })
}

function ensurePageDragPreviewTarget({ direction, viewWidth = null, viewHeight = null, hidden = false }) {
  if (!direction) return null
  const targetIndex = this.adjacentReadableSectionIndex(direction)
  if (targetIndex == null) return null
  const { width, height } = this.pageDragPreviewDimensions(viewWidth, viewHeight)
  const side = direction === 'previous' ? 'left' : 'right'
  const palette = readerThemePalette(this.readerSettings?.theme)
  const curlEnabled = readerDragAnimationModeAllowsCurl(this.readerDragAnimationModeValue)
  const { layer, frame } = this.ensurePageDragPreviewLayer({ curlEnabled })
  const targetKey = this.buildPageDragPreviewTargetKey(targetIndex, direction, width, height)
  if (
    frame?.dataset?.navicPageDragPreviewLoadedKey === targetKey &&
    frame?.contentDocument?.body &&
    this.pageDragPreviewReadyKey !== targetKey
  ) {
    this.pageDragPreviewReadyKey = targetKey
  }

  layer.dataset.navicPageDragPreviewMode = 'boundary'
  layer.dataset.navicPageDragPreviewDirection = direction
  layer.dataset.navicPageDragPreviewSide = side
  layer.dataset.navicPageDragPreviewTargetIndex = String(targetIndex)
  setStylesImportant(layer, {
    position: 'fixed',
    top: '0px',
    left: hidden ? '-1px' : '0px',
    width: hidden ? '1px' : `${width}px`,
    height: `${height}px`,
    'min-height': `${height}px`,
    overflow: 'hidden',
    opacity: hidden ? '0' : '1',
    'z-index': '2147483642',
    'pointer-events': 'none',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'box-sizing': 'border-box',
  })
  setStylesImportant(frame, {
    position: 'absolute',
    top: '0px',
    left: '0px',
    width: `${width}px`,
    height: `${height}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'pointer-events': 'none',
  })

  if (this.pageDragPreviewTargetKey !== targetKey) {
    this.pageDragPreviewTargetKey = targetKey
    this.pageDragPreviewReadyKey = ''
    delete frame.dataset.navicPageDragPreviewLoadedKey
    const token = ++this.pageDragPreviewLoadToken
    this.loadPageDragPreviewFrame(frame, targetIndex, direction, token, targetKey)
  }
  return { layer, frame, targetIndex, targetKey, side, width, height, palette }
}

function readerRendererPageStride(renderer, { width, height, vertical } = {}) {
  const rendererSize = Number(renderer?.size)
  if (Number.isFinite(rendererSize) && rendererSize > 0) {
    return Math.max(1, Math.round(rendererSize))
  }
  const viewSize = Number(renderer?.viewSize)
  const pages = Number(renderer?.pages)
  if (
    Number.isFinite(viewSize) &&
    viewSize > 0 &&
    Number.isFinite(pages) &&
    pages > 0
  ) {
    return Math.max(1, Math.round(viewSize / pages))
  }
  return Math.max(1, Math.round(Number(vertical ? height : width) || 1))
}

function pageDragInteriorPreviewScroll(doc, { direction, width, height, vertical, renderer }) {
  const scroll = pageDragCurlSnapshotScroll(doc)
  const axisStep = readerRendererPageStride(renderer, { width, height, vertical })
  const rendererStart = Number(renderer?.start)
  const baseAxisScroll = Number.isFinite(rendererStart) && rendererStart >= 0
    ? Math.round(rendererStart)
    : (vertical ? scroll.y : scroll.x)
  const sign = direction === 'previous' ? -1 : 1
  const targetX = vertical ? scroll.x : Math.max(0, baseAxisScroll + (axisStep * sign))
  const targetY = vertical ? Math.max(0, baseAxisScroll + (axisStep * sign)) : scroll.y
  return { x: targetX, y: targetY, axisStep }
}

function pageDragCurrentRendererScroll(doc, { width, height, vertical, renderer }) {
  const scroll = pageDragCurlSnapshotScroll(doc)
  const axisStep = readerRendererPageStride(renderer, { width, height, vertical })
  const rendererStart = Number(renderer?.start)
  const baseAxisScroll = Number.isFinite(rendererStart) && rendererStart >= 0
    ? Math.round(rendererStart)
    : (vertical ? scroll.y : scroll.x)
  return {
    x: vertical ? scroll.x : Math.max(0, baseAxisScroll),
    y: vertical ? Math.max(0, baseAxisScroll) : scroll.y,
    axisStep,
  }
}

function pageDragMappedPreviewScroll(frame, doc, targetScroll, { renderer, vertical }) {
  const axisStep = Math.max(1, Math.round(Number(targetScroll?.axisStep) || Number(renderer?.size) || 1))
  const rendererViewSize = Number(renderer?.viewSize)
  const sourceMax = Number.isFinite(rendererViewSize) && rendererViewSize > axisStep
    ? rendererViewSize - axisStep
    : 0
  const root = doc?.documentElement
  const body = doc?.body
  const flow = doc?.querySelector?.('[data-navic-page-curl-snapshot-flow="true"]') || body
  const frameRect = typeof frame?.getBoundingClientRect === 'function'
    ? frame.getBoundingClientRect()
    : null
  const frameWidth = Number(frame?.clientWidth || frameRect?.width || 0)
  const frameHeight = Number(frame?.clientHeight || frameRect?.height || 0)
  const flowWidth = flow && flow !== root && flow !== body
    ? Number(flow.offsetWidth || flow.scrollWidth || 0)
    : 0
  const flowHeight = flow && flow !== root && flow !== body
    ? Number(flow.offsetHeight || flow.scrollHeight || 0)
    : 0
  const cloneScrollWidth = Number(flowWidth || root?.scrollWidth || flow?.scrollWidth || flow?.offsetWidth || body?.scrollWidth || 0)
  const cloneScrollHeight = Number(flowHeight || root?.scrollHeight || flow?.scrollHeight || flow?.offsetHeight || body?.scrollHeight || 0)
  const cloneMaxX = Math.max(0, cloneScrollWidth - Math.max(1, frameWidth))
  const cloneMaxY = Math.max(0, cloneScrollHeight - Math.max(1, frameHeight))
  const mapAxis = (value, cloneMax) => {
    const numeric = Math.max(0, Math.round(Number(value) || 0))
    if (!sourceMax || !cloneMax) return numeric
    return Math.max(0, Math.min(cloneMax, Math.round((numeric / sourceMax) * cloneMax)))
  }
  return {
    x: vertical ? Math.max(0, Math.round(Number(targetScroll?.x) || 0)) : mapAxis(targetScroll?.x, cloneMaxX),
    y: vertical ? mapAxis(targetScroll?.y, cloneMaxY) : Math.max(0, Math.round(Number(targetScroll?.y) || 0)),
    sourceMax,
    cloneMaxX,
    cloneMaxY,
  }
}

function applyPageDragPreviewDocumentOffset(frame, doc, mappedScroll) {
  const root = doc?.documentElement
  const body = doc?.body
  const flow = doc?.querySelector?.('[data-navic-page-curl-snapshot-flow="true"]') || body
  if (!frame || !root || !body) return false
  const x = Math.max(0, Math.round(Number(mappedScroll?.x) || 0))
  const y = Math.max(0, Math.round(Number(mappedScroll?.y) || 0))
  try {
    frame.contentWindow?.scrollTo?.(0, 0)
  } catch {
    // The explicit flow transform below owns the visual page position when iframe scrolling is unavailable.
  }
  setStylesImportant(root, {
    transform: 'none',
    'transform-origin': '0 0',
  })
  setStylesImportant(body, {
    position: 'static',
    left: 'auto',
    top: 'auto',
    transform: 'none',
  })
  if (flow) {
    setStylesImportant(flow, {
      transform: `translate(${-x}px, ${-y}px)`,
      'transform-origin': '0 0',
    })
  }
  return true
}

function syncPageDragInteriorPreviewFrame(frame, renderer, { direction, width, height, vertical, palette }) {
  const contents = typeof renderer?.getContents === 'function' ? (renderer.getContents() || []) : []
  const content = contents.find(item => item?.doc)
  const doc = content?.doc
  if (!frame || !doc?.documentElement) {
    if (frame) {
      frame.dataset.navicPageDragPreviewFrameMode = 'interior'
      frame.dataset.navicPageDragPreviewFrameReady = 'false'
      frame.dataset.navicPageDragPreviewFrameTextLength = '0'
    }
    return false
  }
  const targetScroll = pageDragInteriorPreviewScroll(doc, { direction, width, height, vertical, renderer })
  const key = [
    pageDragCurlSnapshotKey({ role: 'interior-underneath', direction, width, height, content, doc, renderer }),
    targetScroll.x,
    targetScroll.y,
  ].join('|')
  setStylesImportant(frame, {
    position: 'absolute',
    top: '0px',
    left: '0px',
    width: `${Math.max(1, Math.round(Number(width) || 1))}px`,
    height: `${Math.max(1, Math.round(Number(height) || 1))}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette?.background || 'transparent',
    'background-color': palette?.background || 'transparent',
    color: palette?.foreground || 'inherit',
    'pointer-events': 'none',
  })
  frame.dataset.navicPageDragPreviewFrameMode = 'interior'
  frame.dataset.navicPageDragPreviewFrameDirection = direction || ''
  frame.dataset.navicPageDragPreviewFrameTargetScrollX = String(targetScroll.x)
  frame.dataset.navicPageDragPreviewFrameTargetScrollY = String(targetScroll.y)
  frame.dataset.navicPageDragPreviewFrameAxisStep = String(targetScroll.axisStep)
  frame.dataset.navicPageDragPreviewFrameRendererPage = String(Number(renderer?.page) || '')
  frame.dataset.navicPageDragPreviewFrameRendererPages = String(Number(renderer?.pages) || '')
  const markFrameReady = () => {
    try {
      const frameDoc = frame.contentDocument
      const text = frameDoc?.body?.textContent?.replace(/\s+/g, ' ').trim() || ''
      const mappedScroll = pageDragMappedPreviewScroll(frame, frameDoc, targetScroll, { renderer, vertical })
      frame.dataset.navicPageDragPreviewFrameMappedScrollX = String(mappedScroll.x)
      frame.dataset.navicPageDragPreviewFrameMappedScrollY = String(mappedScroll.y)
      frame.dataset.navicPageDragPreviewFrameSourceMax = String(mappedScroll.sourceMax)
      frame.dataset.navicPageDragPreviewFrameCloneMaxX = String(mappedScroll.cloneMaxX)
      frame.dataset.navicPageDragPreviewFrameCloneMaxY = String(mappedScroll.cloneMaxY)
      applyPageDragPreviewDocumentOffset(frame, frameDoc, mappedScroll)
      frame.dataset.navicPageDragPreviewFrameReady = frameDoc?.body ? 'true' : 'false'
      frame.dataset.navicPageDragPreviewFrameTextLength = String(text.length)
    } catch {
      frame.dataset.navicPageDragPreviewFrameReady = 'false'
      frame.dataset.navicPageDragPreviewFrameTextLength = '0'
    }
    return frame.dataset.navicPageDragPreviewFrameReady === 'true'
  }
  if (frame.dataset.navicPageDragPreviewFrameKey !== key) {
    frame.dataset.navicPageDragPreviewFrameKey = key
    frame.dataset.navicPageDragPreviewFrameReady = 'false'
    frame.dataset.navicPageDragPreviewFrameTextLength = '0'
    frame.onload = () => { markFrameReady() }
    frame.removeAttribute('src')
    frame.srcdoc = pageDragCurlSnapshotHtml(doc, {
      width,
      height,
      axisStep: targetScroll.axisStep,
      viewSize: Number(renderer?.viewSize),
    })
    requestAnimationFrame(() => { markFrameReady() })
  } else if (frame.contentDocument?.body) {
    markFrameReady()
  }
  return frame.dataset.navicPageDragPreviewFrameReady === 'true'
}

function ensureInteriorPageDragPreviewTarget({ direction, renderer, viewWidth = null, viewHeight = null }) {
  if (!direction || !this.readerRendererReadyForPageDrag(renderer)) return null
  const { width, height } = this.pageDragPreviewDimensions(viewWidth, viewHeight)
  const side = direction === 'previous' ? 'left' : 'right'
  const vertical = this.readerFlowModeValue === ReaderFlowPagedVertical
  const palette = readerThemePalette(this.readerSettings?.theme)
  const curlEnabled = readerDragAnimationModeAllowsCurl(this.readerDragAnimationModeValue)
  const { layer, frame } = this.ensurePageDragPreviewLayer({ curlEnabled })
  const ready = this.syncPageDragInteriorPreviewFrame(frame, renderer, {
    direction,
    width,
    height,
    vertical,
    palette,
  })
  layer.dataset.navicPageDragPreviewMode = 'interior'
  layer.dataset.navicPageDragPreviewDirection = direction
  layer.dataset.navicPageDragPreviewSide = side
  layer.dataset.navicPageDragPreviewTargetIndex = ''
  return { layer, frame, targetIndex: null, targetKey: '', side, width, height, palette, ready }
}

function preloadPageDragPreviewTargets(label = 'unknown') {
  if (!this.view || this.shellCoverVisible) {
    this.removePageDragPreviewLayer()
    return
  }
  const renderer = this.view?.renderer
  if (!this.readerRendererReadyForPageDrag(renderer)) {
    this.removePageDragPreviewLayer()
    return
  }
  const directions = ['previous', 'next']
    .filter(direction => this.safeNativeDragPreviewAtSectionBoundary(renderer, direction))
  if (!directions.length) {
    this.removePageDragPreviewLayer()
    return
  }
  const direction = directions.includes('next') ? 'next' : directions[0]
  const preview = this.ensurePageDragPreviewTarget({ direction, hidden: true })
  if (preview) {
    readerTrace('page-drag-preview:preload', {
      label,
      direction,
      targetIndex: preview.targetIndex,
      currentIndex: this.currentLoadedSectionIndex(),
    })
  }
}

function updatePageDragPreviewLayer({ direction, deltaX, deltaY, viewWidth, viewHeight, renderer }) {
  if (!direction) {
    this.removePageDragPreviewLayer()
    return
  }
  const atSectionBoundary = this.safeNativeDragPreviewAtSectionBoundary(renderer, direction)
  const preview = atSectionBoundary
    ? this.ensurePageDragPreviewTarget({ direction, viewWidth, viewHeight })
    : this.ensureInteriorPageDragPreviewTarget({ direction, renderer, viewWidth, viewHeight })
  if (!preview) {
    this.removePageDragPreviewLayer()
    return
  }
  const { layer, frame, targetIndex, targetKey, side, width, height, palette } = preview
  const ready = atSectionBoundary
    ? this.pageDragPreviewReadyKey === targetKey
    : preview.ready === true
  const vertical = this.readerFlowModeValue === ReaderFlowPagedVertical
  const exposedWidth = vertical ? width : Math.max(1, Math.min(width, Math.round(Math.abs(Number(deltaX) || 0))))
  const exposedHeight = vertical ? Math.max(1, Math.min(height, Math.round(Math.abs(Number(deltaY) || 0)))) : height
  const left = vertical || side !== 'right' ? 0 : width - exposedWidth
  const top = vertical && direction === 'next' ? height - exposedHeight : 0
  const curlEnabled = readerDragAnimationModeAllowsCurl(this.readerDragAnimationModeValue)
  if (curlEnabled) {
    this.applyPageDragCurlMetrics(layer, {
      direction,
      deltaX,
      deltaY,
      width,
      height,
      vertical,
    })
    this.applyPageDragCurlSheet(layer, {
      direction,
      width,
      height,
      vertical,
      palette,
    })
  } else {
    clearPageDragCurlState(layer)
  }
  layer.dataset.navicPageDragPreviewReady = String(ready)
  if (curlEnabled) {
    this.syncPageDragCurlSnapshots(layer, {
      renderer,
      frame,
      ready,
      mode: layer.dataset.navicPageCurlSheetMode,
      direction,
      width,
      height,
      palette,
    })
  }
  const frameLeft = vertical || side !== 'right' ? 0 : -(width - exposedWidth)
  const frameTop = vertical && direction === 'next' ? -(height - exposedHeight) : 0
  const previewTextureScrollOffset = pageDragPreviewTextureScrollOffset({
    direction,
    frameLeft,
    frameTop,
    width,
    height,
    vertical,
    readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
  })
  this.syncPageDragPreviewTextureLayers(layer, previewTextureScrollOffset)
  if (!ready) {
    const fallbackWidth = exposedWidth
    const fallbackHeight = exposedHeight
    layer.dataset.navicPageDragPreviewFallback = 'paper'
    layer.dataset.navicPageDragPreviewExposedWidth = String(fallbackWidth)
    layer.dataset.navicPageDragPreviewExposedHeight = String(fallbackHeight)
    setStylesImportant(layer, {
      position: 'fixed',
      top: `${top}px`,
      left: `${left}px`,
      width: `${fallbackWidth}px`,
      height: `${fallbackHeight}px`,
      'min-height': `${fallbackHeight}px`,
      overflow: 'hidden',
      opacity: '1',
      'z-index': '2147483642',
      'pointer-events': 'none',
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
      'box-sizing': 'border-box',
    })
    setStylesImportant(frame, {
      opacity: atSectionBoundary ? '0' : '1',
      'pointer-events': 'none',
    })
    readerTrace(atSectionBoundary ? 'page-drag-preview:underlay-waiting' : 'page-drag-preview:interior-waiting', {
      direction,
      side,
      targetIndex,
      exposedWidth: fallbackWidth,
      exposedHeight: fallbackHeight,
      currentIndex: this.currentLoadedSectionIndex(),
    })
    return false
  }
  const frameLeftPx = `${Math.round(frameLeft)}px`
  const frameTopPx = `${Math.round(frameTop)}px`
  layer.dataset.navicPageDragPreviewFallback = 'false'
  layer.dataset.navicPageDragPreviewExposedWidth = String(exposedWidth)
  layer.dataset.navicPageDragPreviewExposedHeight = String(exposedHeight)
  setStylesImportant(layer, {
    position: 'fixed',
    top: `${top}px`,
    left: `${left}px`,
    width: `${exposedWidth}px`,
    height: `${exposedHeight}px`,
    'min-height': `${exposedHeight}px`,
    overflow: 'hidden',
    'z-index': '2147483642',
    'pointer-events': 'none',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'box-sizing': 'border-box',
  })
  setStylesImportant(frame, {
    position: 'absolute',
    top: frameTopPx,
    left: frameLeftPx,
    width: `${width}px`,
    height: `${height}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'pointer-events': 'none',
    opacity: '1',
  })
  readerTrace('page-drag-preview:underlay', {
    direction,
    side,
    targetIndex,
    exposedWidth,
    exposedHeight,
    ready,
    mode: atSectionBoundary ? 'boundary' : 'interior',
    currentIndex: this.currentLoadedSectionIndex(),
  })
  return true
}

function previewPageDrag(command) {
  if (!this.view || this.shellCoverVisible) return
  const renderer = this.view?.renderer
  if (!this.readerRendererReadyForPageDrag(renderer)) return
  const phase = command?.phase === 'release'
    ? 'release'
    : command?.phase === 'cancel'
      ? 'cancel'
      : 'update'
  if (phase === 'cancel') {
    const previousPreview = this.nativePageDragPreview?.renderer === renderer
      ? this.nativePageDragPreview
      : null
    const previousDelta = previousPreview
      ? {
        x: Number(previousPreview.deltaX) || 0,
        y: Number(previousPreview.deltaY) || 0,
      }
      : { x: 0, y: 0 }
    const basePosition = Number(previousPreview?.basePosition)
    if (previousPreview?.live === true && Number.isFinite(basePosition)) {
      renderer.containerPosition = basePosition
      readerTrace('page-drag-preview:live-reset', {
        phase: 'cancel',
        basePosition,
        position: renderer.containerPosition,
      })
    }
    readerTrace('page-drag-preview:cancel', {
      deltaX: previousDelta.x,
      deltaY: previousDelta.y,
    })
    this.nativePageDragPreview = null
    this.pendingPageDragPreviewCommand = null
    this.removePageDragPreviewLayer()
    this.surfacePaperTextureTurnDirection = null
    this.surfacePaperTextureFallbackDirection = null
    this.surfaceLiveDragActive = false
    this.surfaceLiveDragOffset = { x: 0, y: 0 }
    this.renderSurfacePaperTextureLayers()
    return
  }
  if (
    this.readerDragAnimationModeValue === ReaderDragAnimationNone ||
    this.readerDragAnimationModeValue === ReaderDragAnimationCanvas
  ) {
    this.nativePageDragPreview = null
    this.pendingPageDragPreviewCommand = null
    this.removePageDragPreviewLayer()
    this.surfacePaperTextureTurnDirection = null
    this.surfacePaperTextureFallbackDirection = null
    this.surfaceLiveDragActive = false
    this.surfaceLiveDragOffset = { x: 0, y: 0 }
    this.renderSurfacePaperTextureLayers()
    return
  }
  if (phase === 'release') {
    const previousPreview = this.nativePageDragPreview?.renderer === renderer
      ? this.nativePageDragPreview
      : null
    const previousDelta = previousPreview
      ? {
        x: Number(previousPreview.deltaX) || 0,
        y: Number(previousPreview.deltaY) || 0,
      }
      : { x: 0, y: 0 }
    const releaseDeltaX = Number(command?.deltaX)
    const releaseDeltaY = Number(command?.deltaY)
    const releaseTextureDirection = readerPaperTextureDragDirection({
      deltaX: Number.isFinite(releaseDeltaX) ? releaseDeltaX : previousDelta.x,
      deltaY: Number.isFinite(releaseDeltaY) ? releaseDeltaY : previousDelta.y,
      flowMode: this.readerFlowModeValue,
      readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
      threshold: 1,
    })
    if (releaseTextureDirection) {
      this.surfacePaperTextureFallbackDirection = releaseTextureDirection
      readerTrace('texture:drag-direction', {
        direction: releaseTextureDirection,
        source: 'native-preview-release',
      })
    }
    readerTrace('page-drag-preview:release', {
      deltaX: previousDelta.x,
      deltaY: previousDelta.y,
      live: previousPreview?.live === true,
    })
    let liveSnapInProgress = false
    if (previousPreview?.live === true) {
      if (releaseTextureDirection && typeof renderer.snap === 'function') {
        const velocity = readerNativeDragSnapVelocity({
          direction: releaseTextureDirection,
          flowMode: this.readerFlowModeValue,
          readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
        })
        this.suppressNativeDragCommittedPageTurn = releaseTextureDirection
        renderer.snap(velocity.vx, velocity.vy)
        liveSnapInProgress = true
        readerTrace('page-drag-preview:live-snap', {
          direction: releaseTextureDirection,
          velocity,
          position: renderer.containerPosition,
        })
      } else {
        const basePosition = Number(previousPreview.basePosition)
        if (Number.isFinite(basePosition)) renderer.containerPosition = basePosition
        readerTrace('page-drag-preview:live-reset', {
          phase: 'release-without-direction',
          basePosition,
          position: renderer.containerPosition,
        })
      }
    }
    this.nativePageDragPreview = null
    this.pendingPageDragPreviewCommand = null
    this.removePageDragPreviewLayer()
    // Stop using the live-drag offset source so the heuristic (which tracks the
    // animating renderer position) drives the texture through the snap.
    this.surfaceLiveDragActive = false
    this.surfaceLiveDragOffset = { x: 0, y: 0 }
    if (liveSnapInProgress) {
      // Keep the turn direction seeded (it is cleared later by applySurfacePaperTextureUpdate)
      // so the heuristic does not directionless-clamp mid-snap, and run the motion-sync loop
      // so the texture re-renders every frame through the snap instead of freezing (blank)
      // until the deferred ~180ms variant commit. The loop is stopped by
      // applySurfacePaperTextureUpdate when the new page's variant commits.
      this.startSurfacePaperTextureMotionSync('live-drag-snap')
      this.renderSurfacePaperTextureLayers()
    } else {
      this.surfacePaperTextureTurnDirection = null
      this.renderSurfacePaperTextureLayers()
    }
    return
  }
  const deltaX = Number(command?.deltaX)
  const deltaY = Number(command?.deltaY)
  if (!Number.isFinite(deltaX) && !Number.isFinite(deltaY)) return
  const currentDeltaX = Number.isFinite(deltaX) ? deltaX : 0
  const currentDeltaY = Number.isFinite(deltaY) ? deltaY : 0
  const textureDirection = readerPaperTextureDragDirection({
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    flowMode: this.readerFlowModeValue,
    readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
    threshold: 1,
  })
  if (textureDirection) {
    this.surfacePaperTextureTurnDirection = textureDirection
    this.surfacePaperTextureFallbackDirection = textureDirection
    readerTrace('texture:drag-direction', {
      direction: textureDirection,
      source: 'native-preview',
    })
  }
  // Source the previous delta only from a LIVE drag preview. A non-live
  // underlay/boundary preview can carry a stale deltaX that corrupts
  // incrementalDelta on the first live frame and makes the live scroll reveal
  // the wrong adjacent page (e.g. page 7 instead of page 5 on a backward drag).
  const previousLivePreview = this.nativePageDragPreview?.renderer === renderer
    && this.nativePageDragPreview?.live === true
    ? this.nativePageDragPreview
    : null
  const lastDeltaX = previousLivePreview ? (Number(previousLivePreview.deltaX) || 0) : 0
  const lastDeltaY = previousLivePreview ? (Number(previousLivePreview.deltaY) || 0) : 0
  const { incrementalDelta } = readerPageDragPreviewMotion({
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    lastDeltaX,
    lastDeltaY,
    flowMode: this.readerFlowModeValue,
  })
  const boundaryDirection = textureDirection && this.safeNativeDragPreviewAtSectionBoundary(renderer, textureDirection)
    ? textureDirection
    : ''
  let waitingForBoundaryPreview = false
  if (boundaryDirection) {
    const preview = this.ensurePageDragPreviewTarget({
      direction: boundaryDirection,
      viewWidth: command?.viewWidth,
      viewHeight: command?.viewHeight,
      hidden: true,
    })
    const previewReady = preview && this.pageDragPreviewReadyKey === preview.targetKey
    if (!previewReady) {
      this.pendingPageDragPreviewCommand = preview
        ? {
          targetKey: preview.targetKey,
          command: {
            type: 'previewPageDrag',
            phase: 'update',
            deltaX: currentDeltaX,
            deltaY: currentDeltaY,
            viewWidth: command?.viewWidth,
            viewHeight: command?.viewHeight,
          },
        }
        : null
      if (preview?.targetKey) {
        const pending = this.pendingPageDragPreviewCommand
        if (pending?.targetKey === preview.targetKey && this.pageDragPreviewReadyKey === preview.targetKey) {
          this.pendingPageDragPreviewCommand = null
          this.previewPageDrag(pending.command)
        }
      }
      waitingForBoundaryPreview = true
      readerTrace('page-drag-preview:underlay-waiting', {
        direction: boundaryDirection,
        targetIndex: preview?.targetIndex ?? null,
        currentIndex: this.currentLoadedSectionIndex(),
      })
    }
  }
  if (!boundaryDirection && textureDirection && !readerDragAnimationModeAllowsCurl(this.readerDragAnimationModeValue)) {
    // Seed basePosition only from a live preview; otherwise anchor to the current
    // renderer position so a prior non-live (underlay/boundary) preview can't shift
    // the live scroll window by a page.
    const basePosition = previousLivePreview && Number.isFinite(Number(previousLivePreview.basePosition))
      ? Number(previousLivePreview.basePosition)
      : Number(renderer.containerPosition)
    if (Number.isFinite(incrementalDelta.x) || Number.isFinite(incrementalDelta.y)) {
      renderer.scrollBy(
        Number.isFinite(incrementalDelta.x) ? -incrementalDelta.x : 0,
        Number.isFinite(incrementalDelta.y) ? -incrementalDelta.y : 0
      )
    }
    // Frame-lock the paper texture + border-overlay shadow to the same gesture
    // delta that just moved the text. Accumulate the exact incrementalDelta fed
    // to renderer.scrollBy (reset on the first move of this live drag) and apply
    // it synchronously so texture and text share one displacement source.
    if (!previousLivePreview) {
      this.surfaceLiveDragOffset = { x: 0, y: 0 }
    }
    this.surfaceLiveDragOffset = {
      x: (Number(this.surfaceLiveDragOffset?.x) || 0) + (Number.isFinite(incrementalDelta.x) ? incrementalDelta.x : 0),
      y: (Number(this.surfaceLiveDragOffset?.y) || 0) + (Number.isFinite(incrementalDelta.y) ? incrementalDelta.y : 0),
    }
    this.surfaceLiveDragActive = true
    this.syncMovingPageTextureSurface('live-drag')
    this.removePageDragPreviewLayer()
    this.nativePageDragPreview = {
      deltaX: currentDeltaX,
      deltaY: currentDeltaY,
      renderer,
      live: true,
      basePosition,
    }
    readerTrace('page-drag-preview:live-scroll', {
      phase,
      direction: textureDirection,
      deltaX: currentDeltaX,
      deltaY: currentDeltaY,
      incrementalDeltaX: incrementalDelta.x,
      incrementalDeltaY: incrementalDelta.y,
      basePosition,
      position: renderer.containerPosition,
      start: renderer.start,
      end: renderer.end,
      viewSize: renderer.viewSize,
    })
    return
  }
  this.updatePageDragPreviewLayer({
    direction: textureDirection,
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    viewWidth: command?.viewWidth,
    viewHeight: command?.viewHeight,
    renderer,
  })
  this.nativePageDragPreview = phase === 'release'
    ? null
    : { deltaX: currentDeltaX, deltaY: currentDeltaY, renderer }
  readerTrace('page-drag-preview', {
    phase,
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    incrementalDeltaX: incrementalDelta.x,
    incrementalDeltaY: incrementalDelta.y,
    start: renderer.start,
    end: renderer.end,
    viewSize: renderer.viewSize,
    source: waitingForBoundaryPreview ? 'boundary-preview-loading' : 'native-preview',
  })
}

async function scrollViewport(direction) {
  if (!this.view) return
  const scrollDirection = direction === 'up' ? 'up' : 'down'
  const renderer = this.view?.renderer
  if (!renderer?.scrolled || typeof renderer.scrollBy !== 'function') {
    return scrollDirection === 'down' ? this.nextPage() : this.previousPage()
  }
  const viewportSize = Number(renderer.size) || Number(readerViewportSize().height) || 0
  const scrollDistance = Math.max(1, Math.round(viewportSize * ViewportScrollStepRatio))
  const delta = scrollDirection === 'down' ? scrollDistance : -scrollDistance
  const scrollsAlongHeight = renderer.sideProp !== 'width'
  log('viewport-scroll:start', scrollDirection, `distance=${scrollDistance}`)
  readerTrace('viewport-scroll:start', {
    direction: scrollDirection,
    distance: scrollDistance,
    start: renderer.start,
    end: renderer.end,
    viewSize: renderer.viewSize,
  })
  if (scrollsAlongHeight) {
    renderer.scrollBy(delta, 0)
  } else {
    renderer.scrollBy(0, delta)
  }
  this.applyReaderViewportLayout(`viewport-scroll:${scrollDirection}`)
  requestAnimationFrame(() => {
    this.logContentLayout(`viewport-scroll:${scrollDirection}`)
    readerTrace('viewport-scroll:done', {
      direction: scrollDirection,
      start: renderer.start,
      end: renderer.end,
      viewSize: renderer.viewSize,
    })
    log('viewport-scroll:done', scrollDirection)
  })
}

function turnPage(direction) {
  if (this.suppressNativeDragCommittedPageTurn === direction) {
    this.suppressNativeDragCommittedPageTurn = null
    readerTrace('page-turn:suppressed-after-native-drag', {
      direction,
      position: this.currentRendererContainerPosition?.() ?? null,
    })
    return Promise.resolve()
  }
  if (this.shellCoverVisible && direction === 'next') {
    log('page-turn:shell-cover-hide', direction)
    this.hideShellCover()
    return
  }
  if (this.shellCoverVisible && direction === 'previous') {
    log('page-turn:shell-cover-boundary', direction)
    return
  }
  if (direction === 'previous' && this.canReturnToShellCover()) {
    log('page-turn:shell-cover-return', direction)
    this.showShellCover()
    return
  }
  if (this.pageTurnPromise) {
    if (this.view?.isFixedLayout === true && this.pageTurnDirection === direction) {
      log('page-turn:coalesced', direction)
      readerTrace('page-turn:coalesced', {
        direction,
        navigationIndex: this.fixedLayoutNavigationPageIndex,
        rendererIndex: this.fixedLayoutCurrentPageIndex(),
      })
      return this.pageTurnPromise
    }
    log('page-turn:queued', direction)
    readerTrace('page-turn:queued', {
      direction,
      navigationIndex: this.fixedLayoutNavigationPageIndex,
      rendererIndex: this.fixedLayoutCurrentPageIndex(),
    })
    return new Promise((resolve, reject) => {
      this.pageTurnQueue.push({ direction, resolve, reject })
    })
  }
  return this.startPageTurn(direction)
}

function startPageTurn(direction) {
  readerTrace('page-turn:start-request', {
    direction,
    hasPromise: Boolean(this.pageTurnPromise),
    queueLength: this.pageTurnQueue.length,
  })
  this.cancelPendingExactPageTurnSettlement('ordinary-page-turn')
  this.cancelPendingCommittedRelocation()
  this.reflowablePageTurnNavigationPromise = null
  this.pageTurnAdjacentFallbackPromise = null
  this.pageTurnInProgress = true
  this.pageTurnDirection = direction
  const currentPageIndex = Number(this.currentPagePosition?.pageIndex)
  this.pageTurnTargetPageIndex = Number.isFinite(currentPageIndex)
    ? Math.max(0, Math.floor(currentPageIndex) + (direction === 'previous' ? -1 : 1))
    : null
  this.surfacePaperTextureTurnDirection = direction
  this.surfacePaperTextureFallbackDirection = direction
  this.startSurfacePaperTextureMotionSync('page-turn-animation')
  const turnPromise = Promise.resolve().then(() => this.performPageTurn(direction))
  let completionPromise = null
  completionPromise = turnPromise.then(async () => {
    const adjacentFallback = this.pageTurnAdjacentFallbackPromise
    if (adjacentFallback) {
      readerTrace('page-turn:await-adjacent-fallback', { direction })
      await adjacentFallback
    }
  }).finally(() => {
    if (this.pageTurnPromise === completionPromise) this.pageTurnPromise = null
    this.pageTurnInProgress = false
    if (this.pageTurnDirection === direction) this.pageTurnDirection = null
    this.scheduleSettledControlledPageTurnRelocation(direction)
    readerTrace('page-turn:settled', {
      direction,
      navigationIndex: this.fixedLayoutNavigationPageIndex,
      rendererIndex: this.fixedLayoutCurrentPageIndex(),
    })
    this.startNextQueuedPageTurn()
  })
  this.pageTurnPromise = completionPromise
  readerTrace('page-turn:promise-set', {
    direction,
    queueLength: this.pageTurnQueue.length,
  })
  return completionPromise
}

function startNextQueuedPageTurn() {
  if (this.pageTurnPromise || this.pageTurnQueue.length === 0) return
  const next = this.pageTurnQueue.shift()
  this.startPageTurn(next.direction).then(next.resolve, next.reject)
}

function issueReflowablePageTurn(direction) {
  const readiness = this.readerReflowablePageTurnReadiness()
  if (!readiness.ready) {
    log('page-turn:deferred-renderer-not-ready', direction, readiness.reason)
    readerTrace('page-turn:deferred-renderer-not-ready', {
      direction,
      reason: readiness.reason,
      message: readiness.message,
    })
    this.applyReaderViewportLayout(`page-turn:${direction}:deferred`)
    this.retryDeferredReflowablePageTurn(direction)
    return false
  }
  const navigationPromise = direction === 'next'
    ? this.view?.next?.()
    : this.view?.prev?.()
  this.reflowablePageTurnNavigationPromise = navigationPromise?.catch(error => {
    reportError(error, 'navigation_failed')
    return false
  }) ?? null
  return true
}

async function performPageTurn(direction) {
  if (!this.view) return
  const previousFixedLayoutPageIndex = this.fixedLayoutNavigationPageIndex
  const previousFixedLayoutDirection = this.fixedLayoutNavigationDirection
  let directFixedLayoutPageTarget = null
  let controlledRelocationOwner = null
  try {
    log('page-turn:start', direction)
    directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)
    if (directFixedLayoutPageTarget != null) {
      log('page-turn:fixed-direct', direction, directFixedLayoutPageTarget)
      readerTrace('page-turn:fixed-direct', {
        direction,
        target: directFixedLayoutPageTarget,
        navigationIndex: this.fixedLayoutNavigationPageIndex,
        rendererIndex: this.fixedLayoutCurrentPageIndex(),
      })
      this.fixedLayoutNavigationPageIndex = directFixedLayoutPageTarget
      this.fixedLayoutNavigationDirection = direction
      const committed = await this.view.goTo({ index: directFixedLayoutPageTarget })
      if (committed === false) {
        this.fixedLayoutNavigationPageIndex = previousFixedLayoutPageIndex
        this.fixedLayoutNavigationDirection = previousFixedLayoutDirection
        return false
      }
    } else {
      controlledRelocationOwner = this.beginControlledRelocation(`page-turn:${direction}`)
      const pageTurnIssued = this.issueReflowablePageTurn(direction)
      if (!pageTurnIssued) {
        this.cancelControlledRelocation(controlledRelocationOwner)
        return false
      }
    }
    const reflowableNavigation = this.reflowablePageTurnNavigationPromise
    if (reflowableNavigation) {
      readerTrace('page-turn:await-reflowable-navigation', { direction })
      const committed = await reflowableNavigation
      if (committed === false) {
        this.cancelControlledRelocation(controlledRelocationOwner)
        return false
      }
      this.scheduleControlledRelocationFallback(`page-turn:${direction}`)
    }
    this.recentPageTurnDirection = direction
    this.applyReaderViewportLayout(`page-turn:${direction}`)
    requestAnimationFrame(() => {
      this.logContentLayout(`page-turn:${direction}`)
      if (directFixedLayoutPageTarget != null) {
        this.scheduleCommittedRelocation(this.lastRelocateDetail, `page-turn:${direction}`)
      }
      log('page-turn:done', direction)
    })
    return true
  } catch (error) {
    if (
      directFixedLayoutPageTarget != null &&
      this.fixedLayoutNavigationPageIndex === directFixedLayoutPageTarget &&
      this.fixedLayoutNavigationDirection === direction
    ) {
      this.fixedLayoutNavigationPageIndex = previousFixedLayoutPageIndex
      this.fixedLayoutNavigationDirection = previousFixedLayoutDirection
    }
    this.cancelControlledRelocation(controlledRelocationOwner)
    reportError(error, 'navigation_failed')
    return false
  }
}

function attachScrolledEdgeTurnGestures(doc) {
  if (!doc?.defaultView || doc.defaultView.__navicScrolledEdgeTurnGesturesAttached) return
  doc.defaultView.__navicScrolledEdgeTurnGesturesAttached = true
  let touchState = null
  doc.addEventListener('touchstart', event => {
    const touch = event.changedTouches?.[0]
    if (!touch || event.touches?.length > 1) {
      touchState = null
      return
    }
    touchState = {
      x: touch.screenX ?? touch.clientX ?? 0,
      y: touch.screenY ?? touch.clientY ?? 0,
    }
  }, { capture: true, passive: true })
  doc.addEventListener('touchmove', event => {
    if (!touchState || event.touches?.length > 1) {
      touchState = null
      return
    }
    const touch = event.changedTouches?.[0]
    if (!touch) return
    touchState.lastX = touch.screenX ?? touch.clientX ?? touchState.x
    touchState.lastY = touch.screenY ?? touch.clientY ?? touchState.y
  }, { capture: true, passive: true })
  doc.addEventListener('touchend', event => {
    const state = touchState
    touchState = null
    if (!state) return
    const touch = event.changedTouches?.[0]
    if (!touch) return
    const endX = touch.screenX ?? touch.clientX ?? state.lastX ?? state.x
    const endY = touch.screenY ?? touch.clientY ?? state.lastY ?? state.y
    const deltaX = endX - state.x
    const deltaY = endY - state.y
    const selection = doc.getSelection?.()
    if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return
    if (Math.abs(deltaY) < ScrollEdgeTurnSwipeThreshold || Math.abs(deltaY) <= Math.abs(deltaX)) return
    this.turnScrolledEdgePage(deltaY)
  }, { capture: true, passive: true })
  doc.addEventListener('touchcancel', () => {
    touchState = null
  }, { passive: true })
}

function effectiveReaderDirection() {
  if (this.readerDirectionModeValue === ReaderDirectionLtr || this.readerDirectionModeValue === ReaderDirectionRtl) {
    return this.readerDirectionModeValue
  }
  return this.view?.book?.dir === ReaderDirectionRtl ? ReaderDirectionRtl : ReaderDirectionLtr
}

function turnScrolledEdgePage(deltaY) {
  const renderer = this.view?.renderer
  if (!renderer || !renderer.scrolled) return false
  const atStart = renderer.start <= ScrollEdgeTurnSlop
  const atEnd = renderer.viewSize - renderer.end <= ScrollEdgeTurnSlop
  if (deltaY > ScrollEdgeTurnSwipeThreshold && atStart) {
    log('page-turn:edge-swipe', 'previous', `start=${renderer.start}`)
    void this.previousPage()
    return true
  }
  if (deltaY < -ScrollEdgeTurnSwipeThreshold && atEnd) {
    log('page-turn:edge-swipe', 'next', `remaining=${renderer.viewSize - renderer.end}`)
    post({ type: 'pullUp', source: 'scrolled-edge-swipe' })
    void this.nextPage()
    return true
  }
  return false
}

function overrideReaderRendererValue(renderer, key, value) {
  const ownDescriptor = Object.getOwnPropertyDescriptor(renderer, key)
  Object.defineProperty(renderer, key, {
    configurable: true,
    value,
  })
  return () => {
    if (ownDescriptor) {
      Object.defineProperty(renderer, key, ownDescriptor)
    } else {
      delete renderer[key]
    }
  }
}

function diagnosticScrolledEdgePullUp() {
  const renderer = this.view?.renderer
  if (!renderer) {
    return { posted: false, reason: 'missing-renderer' }
  }
  const restoreRendererValues = [
    overrideReaderRendererValue(renderer, 'scrolled', true),
    overrideReaderRendererValue(renderer, 'start', 100),
    overrideReaderRendererValue(renderer, 'end', 1000),
    overrideReaderRendererValue(renderer, 'viewSize', 1000),
  ]
  try {
    const posted = this.turnScrolledEdgePage(-(ScrollEdgeTurnSwipeThreshold + 10))
    return {
      posted,
      reason: 'diagnostic-scrolled-edge-pull-up',
    }
  } finally {
    restoreRendererValues.reverse().forEach(restore => restore())
  }
}

async function turnFixedLayoutSwipePage(deltaX) {
  if (this.view?.isFixedLayout !== true) return false
  if (Math.abs(deltaX) < FixedLayoutSurfaceSwipeThreshold) return false
  const swipedLeft = deltaX < 0
  const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
  log('page-turn:fixed-swipe', swipedLeft ? 'left' : 'right')
  if (swipedLeft === rtl) {
    await this.previousPage()
  } else {
    await this.nextPage()
  }
  return true
}

export const NavicReaderPageTurnMethods = {
  progressTargetForSections,
  fixedLayoutCurrentPageIndex,
  fixedLayoutNavigationBasePageIndex,
  syncFixedLayoutNavigationPageIndex,
  fixedLayoutAdjacentPageTarget,
  goToProgress,
  goToChapterProgress,
  goToVisualPage,
  replacePendingExactPageTurnSettlement,
  commitPendingExactPageTurnSettlement,
  clearPageTurnLivePresentationReceipt,
  clearPageTurnLivePresentationTarget,
  replacePageTurnLivePresentationTarget,
  pageTurnLivePresentationReceiptTarget,
  pageTurnLivePresentationTargetMatchesCurrent,
  issuePageTurnLivePresentationReceipt,
  restorePageTurnLivePresentationReceipt,
  pageTurnLivePresentationReceipt,
  activeExactPageTurnSettlement,
  maybeCompleteNativePageTurnSettlement,
  peekNativePageTurnSettlement,
  consumeNativePageTurnSettlement,
  cancelPendingExactPageTurnSettlement,
  detachLiveTextPageCommitInvalidationListener,
  attachLiveTextPageCommitInvalidationListener,
  handleLiveTextPageCommitInvalidation,
  nextPage,
  previousPage,
  currentLoadedSectionIndex,
  adjacentReadableSectionIndex,
  handleDuplicatePageTurnRelocation,
  nativeDragPreviewAtSectionBoundary,
  readerRendererReadyForPageDrag,
  safeNativeDragPreviewAtSectionBoundary,
  readerReflowablePageTurnReadiness,
  readerReflowablePageTurnReady,
  clearDeferredReflowablePageTurn,
  retryDeferredReflowablePageTurn,
  ensurePageDragPreviewLayer,
  ensurePageDragPreviewLayerChild,
  ensurePageDragPreviewTextureLayers,
  overridePageDragPreviewTextureLayerBox,
  syncMovingPageTextureSurface,
  syncPageDragPreviewTextureLayers,
  removePageDragPreviewLayer,
  pageDragPreviewDimensions,
  readerPageDragCurlMetrics,
  applyPageDragCurlMetrics,
  applyPageDragCurlSheet,
  pageDragCurlSnapshotScroll,
  pageDragCurlSnapshotHtml,
  pageDragCurlSnapshotKey,
  syncPageDragCurlSnapshotFrame,
  syncPageDragCurlSnapshots,
  buildPageDragPreviewTargetKey,
  loadPageDragPreviewFrame,
  ensurePageDragPreviewTarget,
  pageDragInteriorPreviewScroll,
  syncPageDragInteriorPreviewFrame,
  ensureInteriorPageDragPreviewTarget,
  readerDragAnimationModeAllowsCurl,
  clearPageDragCurlState,
  preloadPageDragPreviewTargets,
  updatePageDragPreviewLayer,
  previewPageDrag,
  scrollViewport,
  turnPage,
  startPageTurn,
  startNextQueuedPageTurn,
  issueReflowablePageTurn,
  performPageTurn,
  attachScrolledEdgeTurnGestures,
  effectiveReaderDirection,
  turnScrolledEdgePage,
  diagnosticScrolledEdgePullUp,
  turnFixedLayoutSwipePage
}
