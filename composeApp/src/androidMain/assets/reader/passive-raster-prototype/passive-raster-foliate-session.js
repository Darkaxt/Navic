import {
  isProductionPassiveRasterTarget,
  ProductionRasterFoliateSessionCore,
} from './production-raster-foliate-session.js'
import {
  requiredSequence,
  requiredString,
  SyntheticRasterFoliateSessionCore,
} from './synthetic-raster-foliate-session.js'

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
      echoedDestinationCommitToken: requiredString(
        manifest.destinationCommitToken,
        'destinationCommitToken',
      ),
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
