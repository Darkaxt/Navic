import '../vendor/foliate-js/view.js'

const MaximumSafeSequence = Number.MAX_SAFE_INTEGER
const MaximumExactCommitAttempts = 4
const SyntheticSectionPageCount = 4

const SyntheticProfiles = Object.freeze({
  'portrait-day': Object.freeze({
    key: 'portrait-day',
    theme: 'day',
    fontSizePercent: 100,
    lineHeight: 1.5,
    maxColumnCount: 1,
  }),
  'landscape-day': Object.freeze({
    key: 'landscape-day',
    theme: 'day',
    fontSizePercent: 100,
    lineHeight: 1.5,
    maxColumnCount: 2,
  }),
  'landscape-night-large': Object.freeze({
    key: 'landscape-night-large',
    theme: 'night',
    fontSizePercent: 132,
    lineHeight: 1.68,
    maxColumnCount: 2,
  }),
})

const SyntheticSections = Object.freeze([
  Object.freeze({ href: 'synthetic-section-0.xhtml', label: 'Synthetic opening' }),
  Object.freeze({ href: 'synthetic-section-1.xhtml', label: 'Synthetic middle' }),
  Object.freeze({ href: 'synthetic-section-2.xhtml', label: 'Synthetic boundary' }),
])

export const requiredString = (value, name) => {
  if (typeof value !== 'string' || value.length === 0) {
    throw new TypeError(`${name} must be a non-empty string`)
  }
  return value
}

export const requiredSequence = (value, name) => {
  if (!Number.isSafeInteger(value) || value < 0 || value > MaximumSafeSequence) {
    throw new TypeError(`${name} must be a safe non-negative integer`)
  }
  return value
}

const boundedHash = value => {
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  let first = 0x811c9dc5
  let second = 0x9e3779b9
  for (let index = 0; index < text.length; index += 1) {
    const code = text.charCodeAt(index)
    first = Math.imul(first ^ code, 0x01000193) >>> 0
    second = Math.imul(second ^ code, 0x85ebca6b) >>> 0
  }
  return `${first.toString(16).padStart(8, '0')}${second.toString(16).padStart(8, '0')}`
}

const syntheticTargetToken = targetKey => `synthetic-target-${boundedHash(targetKey)}`

const targetDefinitions = (() => {
  const byKey = new Map()
  const byOpaqueTarget = new Map()
  SyntheticSections.forEach((section, sectionIndex) => {
    for (let pageIndex = 0; pageIndex < SyntheticSectionPageCount; pageIndex += 1) {
      const key = `section-${sectionIndex}-page-${pageIndex}`
      const opaqueCaptureTarget = syntheticTargetToken(key)
      const target = Object.freeze({
        key,
        opaqueCaptureTarget,
        sectionIndex,
        pageIndex,
        href: `${section.href}#synthetic-page-${pageIndex}`,
      })
      byKey.set(key, target)
      byOpaqueTarget.set(opaqueCaptureTarget, target)
    }
  })
  return Object.freeze({ byKey, byOpaqueTarget })
})()

export const syntheticOpaqueCaptureTarget = targetKey =>
  targetDefinitions.byKey.get(requiredString(targetKey, 'targetKey'))?.opaqueCaptureTarget ?? null

const syntheticPageMarkup = (sectionIndex, pageIndex) => {
  const sequence = Array.from({ length: 9 }, (_, paragraphIndex) => `
    <p>
      Public synthetic paragraph ${paragraphIndex + 1}. This invented fixture exercises
      deterministic Foliate pagination without publication, user, or transcript data.
    </p>
  `).join('')
  return `
    <article id="synthetic-page-${pageIndex}" class="synthetic-page">
      <h1>Synthetic section ${sectionIndex + 1}, leaf ${pageIndex + 1}</h1>
      ${sequence}
    </article>
  `
}

const syntheticSectionDocument = sectionIndex => `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    html, body { margin: 0; padding: 0; }
    body { color: #2e271f; background: #f6efdd; }
    .synthetic-page {
      padding: 3rem 3.5rem;
      break-before: column;
      break-after: column;
    }
    h1 { margin: 0 0 1.2em; font-size: 1.65em; }
    p { margin: 0 0 0.9em; }
  </style>
</head>
<body>
  ${Array.from(
    { length: SyntheticSectionPageCount },
    (_, pageIndex) => syntheticPageMarkup(sectionIndex, pageIndex),
  ).join('')}
</body>
</html>`

const createSyntheticSection = (definition, sectionIndex) => {
  let objectUrl = null
  return {
    id: `synthetic-section-${sectionIndex}`,
    href: definition.href,
    size: 4096,
    linear: 'yes',
    load() {
      if (!objectUrl) {
        objectUrl = URL.createObjectURL(new Blob(
          [syntheticSectionDocument(sectionIndex)],
          { type: 'text/html' },
        ))
      }
      return objectUrl
    },
    unload() {
      if (!objectUrl) return
      URL.revokeObjectURL(objectUrl)
      objectUrl = null
    },
    resolveHref(value) {
      const hash = String(value ?? '').split('#')[1]
      return document => hash ? document.getElementById(hash) : document.body
    },
  }
}

export const createSyntheticPublication = () => {
  const sections = SyntheticSections.map(createSyntheticSection)
  return {
    metadata: Object.freeze({ language: 'en', title: 'Public synthetic raster fixture' }),
    dir: 'ltr',
    rendition: Object.freeze({ layout: 'reflowable' }),
    sections,
    toc: SyntheticSections.map((section, index) => Object.freeze({
      label: section.label,
      href: section.href,
      index,
    })),
    resolveHref(value) {
      const href = String(value ?? '')
      const [sectionHref, hash] = href.split('#')
      const index = SyntheticSections.findIndex(section => section.href === sectionHref)
      if (index < 0) throw new TypeError('Unknown synthetic section target')
      return {
        index,
        anchor: document => hash ? document.getElementById(hash) : document.body,
      }
    },
    isExternal() {
      return false
    },
  }
}

const sessionIdentity = () => {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = new Uint32Array(4)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, value => value.toString(16).padStart(8, '0')).join('')
}

const profileCss = profile => {
  const night = profile.theme === 'night'
  const background = night ? '#17191d' : '#f6efdd'
  const backgroundEdge = night ? '#242930' : '#e5d8bf'
  const foreground = night ? '#e9e2d2' : '#2e271f'
  const accent = night ? '#d0a96f' : '#76512d'
  return `
    :root {
      --synthetic-profile-key: "${profile.key}";
      --synthetic-raster-sentinel: linear-gradient(135deg, ${background}, ${backgroundEdge});
      color: ${foreground} !important;
      background: ${background} !important;
      font-size: ${profile.fontSizePercent}% !important;
      line-height: ${profile.lineHeight} !important;
    }
    body {
      color: ${foreground} !important;
      background-color: ${background} !important;
      background-image: var(--synthetic-raster-sentinel) !important;
      font-family: Georgia, serif !important;
      font-size: 1rem !important;
      line-height: ${profile.lineHeight} !important;
    }
    h1 { color: ${accent} !important; }
  `
}

const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

const stableRuntimeObservation = async (session, receipt) => {
  let previousKey = null
  let stableCount = 0
  for (let attempt = 0; attempt < 90; attempt += 1) {
    if (session.view.renderer?.validateTextPageCommit(receipt) !== true) {
      throw new Error('foliate-commit-invalidated')
    }
    const observation = session.readRuntimeObservation()
    const key = observation ? JSON.stringify(observation) : null
    if (key && key === previousKey) stableCount += 1
    else stableCount = 0
    if (observation && stableCount >= 7) return observation
    previousKey = key
    await nextFrame()
  }
  throw new Error('foliate-observation-not-stable')
}

export class SyntheticRasterFoliateSessionCore {
  constructor(host = document.getElementById('passive-raster-stage')) {
    if (!host) throw new Error('synthetic-host-unavailable')
    this.host = host
    this.sessionId = sessionIdentity()
    this.publicationSessionGeneration = 1
    this.commitSequence = 0
    this.activeProfile = null
    this.activeTarget = null
    this.view = document.createElement('foliate-view')
    this.view.setAttribute('aria-label', 'Public synthetic Foliate raster fixture')
    this.host.replaceChildren(this.view)
    this.openTask = this.open()
  }

  async open() {
    const publication = createSyntheticPublication()
    await this.view.open(publication)
  }

  async applyProfile(profileKey) {
    const profile = SyntheticProfiles[requiredString(profileKey, 'profileKey')]
    if (!profile) throw new TypeError('Unknown synthetic raster profile')
    await this.openTask
    const renderer = this.view.renderer
    renderer.setAttribute('flow', 'paginated')
    renderer.setAttribute('max-column-count', String(profile.maxColumnCount))
    renderer.setAttribute('column-threshold', '720')
    renderer.setAttribute('gap', '0')
    renderer.setAttribute('margin', '0')
    renderer.setAttribute('top-margin', '0')
    renderer.setAttribute('bottom-margin', '0')
    renderer.setStyles(profileCss(profile))
    this.activeProfile = profile
    return profile
  }

  async commitOpaqueTarget(opaqueCaptureTarget, profileKey) {
    const profile = await this.applyProfile(profileKey)
    const resolvedTarget = targetDefinitions.byOpaqueTarget.get(
      requiredString(opaqueCaptureTarget, 'opaqueCaptureTarget'),
    )
    if (!resolvedTarget) throw new TypeError('Unknown synthetic capture target')
    const renderer = this.view.renderer
    for (let attempt = 0; attempt < MaximumExactCommitAttempts; attempt += 1) {
      const result = await renderer.commitTextPage(
        resolvedTarget.sectionIndex,
        resolvedTarget.pageIndex,
        'navigation',
      )
      if (result?.status === 'invalidated') continue
      if (result?.status !== 'committed' ||
          renderer.validateTextPageCommit(result.receipt) !== true) {
        throw new Error('foliate-target-not-committed')
      }
      this.activeTarget = resolvedTarget
      let observation
      try {
        observation = await stableRuntimeObservation(this, result.receipt)
      } catch (failure) {
        if (failure?.message === 'foliate-commit-invalidated') continue
        throw failure
      }
      if (observation.rasterProfileKey !== profile.key) {
        throw new Error('foliate-profile-not-observed')
      }
      this.commitSequence += 1
      return observation
    }
    throw new Error('foliate-target-not-committed')
  }

  readRuntimeObservation() {
    const renderer = this.view.renderer
    const target = this.activeTarget
    const profile = this.activeProfile
    const exactPosition = renderer?.exactTextPagePosition()
    if (!renderer || !target || !profile || !exactPosition) return null
    const content = renderer.getContents?.().find(entry => entry.index === exactPosition.index)
    const contentRoot = content?.doc?.documentElement
    const contentBody = content?.doc?.body
    if (!contentRoot || !contentBody || !content.doc.defaultView ||
        (content.doc.fonts && content.doc.fonts.status !== 'loaded')) return null
    const rootStyle = content.doc.defaultView.getComputedStyle(contentRoot)
    const bodyStyle = content.doc.defaultView.getComputedStyle(contentBody)
    const viewRect = this.view.getBoundingClientRect()
    const density = window.devicePixelRatio || 1
    const viewportWidth = Math.round(viewRect.width * density)
    const viewportHeight = Math.round(viewRect.height * density)
    if (viewportWidth <= 0 || viewportHeight <= 0) return null
    const viewportAndCaptureGeometry = Object.freeze({
      viewportWidth,
      viewportHeight,
      captureLeft: 0,
      captureTop: 0,
      captureRight: viewportWidth,
      captureBottom: viewportHeight,
    })
    const visualPageOrdinal =
      exactPosition.index * SyntheticSectionPageCount + exactPosition.pageIndex
    const paginationFingerprint = boundedHash({
      sectionCount: this.view.book.sections.length,
      sectionIndex: exactPosition.index,
      pageCount: exactPosition.pageCount,
      maxColumnCount: renderer.getAttribute('max-column-count'),
      flow: renderer.getAttribute('flow'),
      viewportWidth,
      viewportHeight,
    })
    const layoutFingerprint = boundedHash({
      fontFamily: bodyStyle.fontFamily,
      fontSize: bodyStyle.fontSize,
      lineHeight: bodyStyle.lineHeight,
      rootFontSize: rootStyle.fontSize,
      maxColumnCount: renderer.getAttribute('max-column-count'),
      columnThreshold: renderer.getAttribute('column-threshold'),
      pageCount: exactPosition.pageCount,
      viewportWidth,
      viewportHeight,
    })
    const decorationFingerprint = boundedHash({
      color: bodyStyle.color,
      backgroundColor: bodyStyle.backgroundColor,
      headingColor: content.doc.defaultView.getComputedStyle(
        content.doc.querySelector('h1'),
      ).color,
    })
    return Object.freeze({
      opaqueCaptureTarget: target.opaqueCaptureTarget,
      visualPageOrdinal,
      rasterProfileKey: profile.key,
      paginationFingerprint,
      layoutFingerprint,
      decorationFingerprint,
      viewportAndCaptureGeometry,
    })
  }

}
