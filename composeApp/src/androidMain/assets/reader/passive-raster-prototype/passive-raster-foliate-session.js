import {
  isProductionPassiveRasterTarget,
  ProductionRasterFoliateSessionCore,
} from './production-raster-foliate-session.js'
import {
  requiredSequence,
  requiredString,
  SyntheticRasterFoliateSessionCore,
} from './synthetic-raster-foliate-session.js'

const captureAuthority = manifest => {
  const hasBasis = Object.hasOwn(manifest, 'captureBasis')
  if (hasBasis === Object.hasOwn(manifest, 'destinationCommitToken')) {
    throw new TypeError('Exactly one capture authority is required')
  }
  if (!hasBasis) {
    const token = requiredString(manifest.destinationCommitToken, 'destinationCommitToken')
    if (!token.trim()) throw new TypeError('Invalid rendered destination token')
    return Object.freeze({ echoedDestinationCommitToken: token })
  }
  const basis = manifest.captureBasis
  const destination = basis?.destinationCommitIdentity
  const layout = basis?.resolvedLayout
  const commandId = requiredString(basis?.commandId, 'commandId')
  const sessionId = requiredString(destination?.foliateSessionId, 'foliateSessionId')
  if (basis?.kind !== 'initial-current-destination' || commandId !== commandId.trim() ||
      sessionId !== sessionId.trim() || sessionId === 'foliate-unbound' ||
      sessionId !== manifest.liveFoliateSessionId ||
      requiredSequence(destination?.commitSequence, 'commitSequence') <= 0 ||
      !layout || requiredSequence(layout.layoutGeneration, 'layoutGeneration') <= 0 ||
      requiredSequence(layout.viewGeneration, 'viewGeneration') <= 0 ||
      requiredSequence(layout.navigationGeneration, 'navigationGeneration') < 0 ||
      requiredSequence(layout.sectionIndex, 'sectionIndex') < 0 ||
      requiredSequence(layout.pageIndex, 'pageIndex') >= requiredSequence(layout.pageCount, 'pageCount') ||
      !Number.isFinite(layout.width) || layout.width <= 0 ||
      !Number.isFinite(layout.height) || layout.height <= 0) {
    throw new TypeError('Invalid initial capture basis')
  }
  return Object.freeze({ echoedCaptureBasis: Object.freeze({
    kind: basis.kind, commandId,
    destinationCommitIdentity: Object.freeze({ foliateSessionId: sessionId, commitSequence: destination.commitSequence }),
    resolvedLayout: Object.freeze({
      layoutGeneration: layout.layoutGeneration, viewGeneration: layout.viewGeneration,
      navigationGeneration: layout.navigationGeneration, sectionIndex: layout.sectionIndex,
      pageIndex: layout.pageIndex, pageCount: layout.pageCount, width: layout.width, height: layout.height,
    }),
  }) })
}

export class PassiveRasterFoliateSession {
  constructor(host = document.getElementById('passive-raster-stage')) {
    this.host = host
    this.core = null
    this.passiveSessionId = null
  }

  coreForCaptureTarget(captureTarget) {
    if (this.core) return this.core
    this.core = isProductionPassiveRasterTarget(captureTarget)
      ? new ProductionRasterFoliateSessionCore(this.host)
      : new SyntheticRasterFoliateSessionCore(this.host)
    return this.core
  }

  async commitCapture(input, signal = null) {
    const manifest = input?.manifest
    if (!manifest || typeof manifest !== 'object') {
      throw new TypeError('manifest must be an object')
    }
    const authority = captureAuthority(manifest)
    const captureTarget = requiredString(input?.captureTarget, 'captureTarget')
    const core = this.coreForCaptureTarget(captureTarget)
    const requestedPassiveSessionId = input?.passiveSessionId == null
      ? core.sessionId
      : requiredString(input.passiveSessionId, 'passiveSessionId')
    if (this.passiveSessionId != null && this.passiveSessionId !== requestedPassiveSessionId) {
      throw new TypeError('Passive session identity cannot be replaced')
    }
    this.passiveSessionId = requestedPassiveSessionId
    const passiveCommitSequence = requiredSequence(
      input?.passiveCommitSequence,
      'passiveCommitSequence',
    )
    const observation = await this.core.commitOpaqueTarget(
      captureTarget,
      requiredString(manifest.rasterProfileKey, 'rasterProfileKey'),
      signal,
    )
    return Object.freeze({
      passiveSessionId: this.passiveSessionId,
      echoedManifestSequence: requiredSequence(manifest.manifestSequence, 'manifestSequence'),
      echoedCaptureEpoch: requiredSequence(manifest.captureEpoch, 'captureEpoch'),
      echoedLiveFoliateSessionId: requiredString(
        manifest.liveFoliateSessionId,
        'liveFoliateSessionId',
      ),
      echoedPublicationSessionGeneration: requiredSequence(
        manifest.publicationSessionGeneration,
        'publicationSessionGeneration',
      ),
      ...authority,
      observedCaptureTarget: observation.opaqueCaptureTarget,
      observedVisualPageOrdinal: observation.visualPageOrdinal,
      observedRasterProfileKey: observation.rasterProfileKey,
      observedPaginationFingerprint: observation.paginationFingerprint,
      observedLayoutFingerprint: observation.layoutFingerprint,
      observedDecorationFingerprint: observation.decorationFingerprint,
      observedViewportAndCaptureGeometry: observation.viewportAndCaptureGeometry,
      echoedRasterGeneration: requiredSequence(manifest.rasterGeneration, 'rasterGeneration'),
      passiveCommitSequence,
    })
  }
}
