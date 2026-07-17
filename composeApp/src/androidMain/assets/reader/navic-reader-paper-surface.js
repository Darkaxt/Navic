import {
  ReaderFlowPaged,
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  readerThemeKey,
  readerThemePalette,
  readerThemeUsesWarmPaperTreatment,
} from './navic-reader-settings-core.js'
export const readerSurfaceSpreadMode = ({
  flowMode = '',
  width: rawWidth = null,
  height: rawHeight = null,
} = {}) => {
  if (flowMode === ReaderFlowScrolled) return 'single'
  if (flowMode === ReaderFlowScrolledGaps) return 'single'
  if (flowMode === ReaderFlowPagedVertical) return 'single'
  const width = Number(rawWidth)
  const height = Number(rawHeight)
  if (Number.isFinite(width) && Number.isFinite(height) && width >= height * 1.12) {
    return 'spread'
  }
  return 'single'
}

export const readerPaperLayoutProfile = ({
  flowMode = '',
  width = null,
  height = null,
  spreadMode = '',
} = {}) => {
  const mode = spreadMode || readerSurfaceSpreadMode({ flowMode, width, height })
  const paged = flowMode === ReaderFlowPaged
  return mode === 'spread'
    ? { mode: 'spread', bindingEdge: 'center' }
    : {
        mode: 'single',
        bindingEdge: 'left',
        backCoverEdge: paged ? 'right' : 'none',
      }
}

export const readerPortraitBindingHintBoxShadow = (settings, profile) => {
  if (profile?.mode !== 'single') return 'none'
  if (settings?.pageEdgesEnabled === false) return 'none'
  if (profile?.bindingEdge === 'right') {
    return 'inset -18px 0 24px -22px rgba(67, 42, 20, .52)'
  }
  return profile?.bindingEdge === 'left'
    ? 'inset 18px 0 24px -22px rgba(67, 42, 20, .52)'
    : 'none'
}

export const readerSurfaceSpreadGutterVisible = ({
  settings,
  spreadMode = '',
  flowMode = '',
  width = null,
  height = null,
} = {}) => {
  if (settings?.pageEdgesEnabled === false) return false
  if (spreadMode === 'spread') return true
  if (spreadMode) return false
  return readerSurfaceSpreadMode({ flowMode, width, height }) === 'spread'
}

export const readerCoverBackdropEnabled = settings => settings?.coverBackdropEnabled !== false

const readerPercentValue = value => `${Number(Number(value).toFixed(4))}%`

export const readerSurfacePageDecorationGeometry = ({
  settings,
  spreadMode = '',
  foliateGap = null,
  shellCoverVisible = false,
  coverTint = null,
  layoutProfile = null,
} = {}) => {
  const parsedGap = Number.parseFloat(String(foliateGap || ''))
  const gapPercent = spreadMode === 'spread' && Number.isFinite(parsedGap)
    ? Math.min(40, Math.max(0, parsedGap))
    : 0
  const outerInsetPercent = gapPercent / 2
  const pageWidthPercent = 50 - outerInsetPercent
  const boundedSpread = gapPercent > 0
  const portraitSingle = spreadMode !== 'spread' &&
    layoutProfile?.mode === 'single' &&
    (layoutProfile?.backCoverEdge === 'left' || layoutProfile?.backCoverEdge === 'right')
  const portraitRevealPercent = portraitSingle ? 1 : 0
  const backCoverRevealPercent = boundedSpread ? outerInsetPercent : portraitRevealPercent
  const backCoverStartPercent = 0
  const backCoverEdge = boundedSpread ? 'both' : portraitSingle ? layoutProfile.backCoverEdge : 'none'
  return {
    boundedSpread,
    gapPercent,
    outerInsetPercent,
    pageWidthPercent,
    backCoverRevealPercent,
    backCoverStartPercent,
    backCoverEdge,
    backCoverVisible: backCoverEdge !== 'none' && shellCoverVisible !== true,
    pages: {
      full: {
        left: '0px',
        width: portraitSingle ? readerPercentValue(100 - portraitRevealPercent) : '100%',
      },
      left: {
        left: boundedSpread ? readerPercentValue(outerInsetPercent) : '0px',
        width: boundedSpread ? readerPercentValue(pageWidthPercent) : '50%',
      },
      right: {
        left: '50%',
        width: boundedSpread ? readerPercentValue(pageWidthPercent) : '50%',
      },
    },
    theme: readerThemeKey(settings?.theme),
    coverTint,
    layoutProfile,
  }
}

const readerColorChannels = value => {
  if (value && typeof value === 'object') {
    const red = Number(value.red)
    const green = Number(value.green)
    const blue = Number(value.blue)
    if ([red, green, blue].every(Number.isFinite)) return { red, green, blue }
  }
  const match = /^#([0-9a-f]{6})$/i.exec(String(value || '').trim())
  if (match) {
    const number = Number.parseInt(match[1], 16)
    return {
      red: (number >> 16) & 255,
      green: (number >> 8) & 255,
      blue: number & 255,
    }
  }
  const rgb = /^rgba?\(\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)/i.exec(String(value || '').trim())
  return rgb ? { red: Number(rgb[1]), green: Number(rgb[2]), blue: Number(rgb[3]) } : null
}

const readerMixedColorChannels = (background, foreground, foregroundWeight) => {
  const base = readerColorChannels(background) || { red: 234, green: 217, blue: 174 }
  const front = readerColorChannels(foreground) || { red: 38, green: 27, blue: 16 }
  const weight = Math.min(1, Math.max(0, Number(foregroundWeight) || 0))
  return {
    red: Math.round(base.red + (front.red - base.red) * weight),
    green: Math.round(base.green + (front.green - base.green) * weight),
    blue: Math.round(base.blue + (front.blue - base.blue) * weight),
  }
}

const readerHueChannel = (p, q, value) => {
  let hue = value
  if (hue < 0) hue += 1
  if (hue > 1) hue -= 1
  if (hue < 1 / 6) return p + (q - p) * 6 * hue
  if (hue < 1 / 2) return q
  if (hue < 2 / 3) return p + (q - p) * (2 / 3 - hue) * 6
  return p
}

export const readerReadableCoverTintChannels = value => {
  const color = readerColorChannels(value)
  if (!color) return null
  const red = color.red / 255
  const green = color.green / 255
  const blue = color.blue / 255
  const maximum = Math.max(red, green, blue)
  const minimum = Math.min(red, green, blue)
  const delta = maximum - minimum
  let hue = 0
  if (delta > 0) {
    if (maximum === red) hue = ((green - blue) / delta + (green < blue ? 6 : 0)) / 6
    else if (maximum === green) hue = ((blue - red) / delta + 2) / 6
    else hue = ((red - green) / delta + 4) / 6
  }
  const sourceLightness = (maximum + minimum) / 2
  const sourceSaturation = delta === 0
    ? 0
    : delta / (1 - Math.abs(2 * sourceLightness - 1))
  const minimumLightness = 0.22
  const minimumSaturation = 0.38
  const lightness = Math.min(0.46, Math.max(minimumLightness, sourceLightness))
  const saturation = Math.min(0.72, Math.max(minimumSaturation, sourceSaturation))
  const q = lightness < 0.5
    ? lightness * (1 + saturation)
    : lightness + saturation - lightness * saturation
  const p = 2 * lightness - q
  return {
    red: Math.round(readerHueChannel(p, q, hue + 1 / 3) * 255),
    green: Math.round(readerHueChannel(p, q, hue) * 255),
    blue: Math.round(readerHueChannel(p, q, hue - 1 / 3) * 255),
  }
}

const readerRgba = (channels, alpha) =>
  `rgba(${channels.red}, ${channels.green}, ${channels.blue}, ${alpha})`

export const readerSurfaceBackCoverPalette = (settings, coverTint) => {
  const theme = readerThemePalette(settings?.theme)
  const warm = readerThemeUsesWarmPaperTreatment(settings?.theme)
  const base = readerReadableCoverTintChannels(coverTint) || theme.background
  return {
    base,
    highlight: readerMixedColorChannels(base, theme.background, 0.26),
    middle: readerMixedColorChannels(base, theme.foreground, 0.04),
    edge: readerMixedColorChannels(base, theme.foreground, warm ? 0.18 : 0.14),
    outer: readerMixedColorChannels(base, theme.foreground, warm ? 0.34 : 0.30),
  }
}

export const readerSurfaceBackCoverBackground = (settings, geometry) => {
  if (!geometry?.backCoverVisible) return 'none'
  const coverPalette = readerSurfaceBackCoverPalette(settings, geometry.coverTint)
  const inset = geometry.outerInsetPercent
  const coverStart = geometry.backCoverStartPercent
  const fade = Math.min(inset, coverStart + Math.min(0.35, geometry.backCoverRevealPercent * 0.4))
  if (geometry.backCoverEdge === 'right') {
    const reveal = Math.min(4, Math.max(0, geometry.backCoverRevealPercent))
    const revealStart = 100 - reveal
    const highlightEnd = revealStart + Math.min(0.12, reveal * 0.12)
    const transitionStart = 100 - Math.min(reveal, Math.min(0.35, reveal * 0.4))
    return `linear-gradient(to right, transparent 0%, transparent ${readerPercentValue(revealStart)}, ${readerRgba(coverPalette.highlight, 0.72)} ${readerPercentValue(revealStart)}, ${readerRgba(coverPalette.edge, 0.96)} ${readerPercentValue(highlightEnd)}, ${readerRgba(coverPalette.base, 0.90)} ${readerPercentValue(transitionStart)}, ${readerRgba(coverPalette.middle, 0.94)} ${readerPercentValue(99.82)}, ${readerRgba(coverPalette.outer, 0.98)} 100%)`
  }
  if (geometry.backCoverEdge === 'left') {
    const reveal = Math.min(4, Math.max(0, geometry.backCoverRevealPercent))
    const highlightStart = Math.max(0, reveal - Math.min(0.12, reveal * 0.12))
    const transitionEnd = Math.min(reveal, Math.min(0.35, reveal * 0.4))
    return `linear-gradient(to right, ${readerRgba(coverPalette.outer, 0.98)} 0%, ${readerRgba(coverPalette.middle, 0.94)} 0.18%, ${readerRgba(coverPalette.base, 0.90)} ${readerPercentValue(transitionEnd)}, ${readerRgba(coverPalette.edge, 0.96)} ${readerPercentValue(highlightStart)}, ${readerRgba(coverPalette.highlight, 0.72)} ${readerPercentValue(reveal)}, transparent ${readerPercentValue(reveal)}, transparent 100%)`
  }
  const reverseCoverStart = 100 - coverStart
  const reverseFade = 100 - fade
  const reverseInset = 100 - inset
  return `linear-gradient(90deg, transparent 0%, transparent ${readerPercentValue(coverStart)}, ${readerRgba(coverPalette.outer, 0.92)} ${readerPercentValue(coverStart)}, ${readerRgba(coverPalette.base, 0.86)} ${readerPercentValue(fade)}, ${readerRgba(coverPalette.middle, 0.82)} ${readerPercentValue(Math.min(inset, fade + 0.16))}, ${readerRgba(coverPalette.edge, 0.90)} ${readerPercentValue(inset)}, transparent ${readerPercentValue(inset)}, transparent ${readerPercentValue(reverseInset)}, ${readerRgba(coverPalette.edge, 0.90)} ${readerPercentValue(reverseInset)}, ${readerRgba(coverPalette.middle, 0.82)} ${readerPercentValue(Math.max(reverseInset, reverseFade - 0.16))}, ${readerRgba(coverPalette.base, 0.86)} ${readerPercentValue(reverseFade)}, ${readerRgba(coverPalette.outer, 0.92)} ${readerPercentValue(reverseCoverStart)}, transparent ${readerPercentValue(reverseCoverStart)}, transparent 100%)`
}

export const readerSurfacePaperBaseBackground = (settings, spreadMode = '') => {
  const palette = readerThemePalette(settings?.theme)
  if (!readerThemeUsesWarmPaperTreatment(settings?.theme)) {
    return { image: 'none', color: palette.background }
  }
  const edgeStrength = spreadMode === 'spread' ? '.10' : '.08'
  return {
    image: `linear-gradient(90deg, rgba(118,76,31,${edgeStrength}) 0%, rgba(255,244,212,.09) 7%, rgba(255,244,212,0) 19%, rgba(255,244,212,0) 81%, rgba(103,65,26,${edgeStrength}) 100%)`,
    color: palette.background,
  }
}
