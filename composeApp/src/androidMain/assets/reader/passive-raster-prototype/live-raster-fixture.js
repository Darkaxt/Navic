import { createBoundedOperationRuntime } from './bounded-operation-runtime.js'
import {
  requiredSequence,
  syntheticOpaqueCaptureTarget,
  SyntheticRasterFoliateSessionCore,
} from './synthetic-raster-foliate-session.js'

class LiveRasterFoliateFixtureSession {
  constructor() {
    this.core = new SyntheticRasterFoliateSessionCore()
    this.manifestSequence = 0
    this.captureEpoch = 0
  }

  async issueLiveManifest(input) {
    const captureTarget = syntheticOpaqueCaptureTarget(input?.targetKey)
    if (!captureTarget) throw new TypeError('Unknown synthetic live target')
    const rasterGeneration = requiredSequence(input?.rasterGeneration, 'rasterGeneration')
    const observation = await this.core.commitOpaqueTarget(captureTarget, input?.profileKey)
    this.manifestSequence = requiredSequence(
      this.manifestSequence + 1,
      'manifestSequence',
    )
    this.captureEpoch = requiredSequence(this.captureEpoch + 1, 'captureEpoch')
    const destinationCommitToken =
      `${this.core.sessionId}-commit-${this.core.commitSequence}`
    const manifest = Object.freeze({
      manifestSequence: this.manifestSequence,
      captureEpoch: this.captureEpoch,
      liveFoliateSessionId: this.core.sessionId,
      publicationSessionGeneration: this.core.publicationSessionGeneration,
      destinationCommitToken,
      opaqueCaptureTarget: observation.opaqueCaptureTarget,
      visualPageOrdinal: observation.visualPageOrdinal,
      rasterProfileKey: observation.rasterProfileKey,
      paginationFingerprint: observation.paginationFingerprint,
      layoutFingerprint: observation.layoutFingerprint,
      decorationFingerprint: observation.decorationFingerprint,
      viewportAndCaptureGeometry: observation.viewportAndCaptureGeometry,
      rasterGeneration,
    })
    return Object.freeze({ manifest, captureTarget: observation.opaqueCaptureTarget })
  }
}

let session = null
const operations = createBoundedOperationRuntime()

const activeSession = () => {
  session ??= new LiveRasterFoliateFixtureSession()
  return session
}

export const startLiveManifest = input => operations.start(
  () => activeSession().issueLiveManifest(input),
)

export const readOperationResult = (operationId, consume = false) =>
  operations.read(operationId, consume)

export const boundedStatus = () => Object.freeze({
  ...operations.boundedStatus(),
  sessionReady: session != null,
})

const api = Object.freeze({
  ready: true,
  startLiveManifest,
  readOperationResult,
  boundedStatus,
})

globalThis.NavicLiveRasterFixture = api
