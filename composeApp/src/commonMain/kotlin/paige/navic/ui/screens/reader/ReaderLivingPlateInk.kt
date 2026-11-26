package paige.navic.ui.screens.reader

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/**
 * How a living plate colours in: ink spreading from the centre of the grey plate (the owner's pick in
 * the colouring lab, D:/Projects/Navic-diagnostics/colour-lab). Tunable from Developer settings.
 */
data class LivingPlateInkTuning(
	val colourMillis: Int = 4_000,
	/** 0 steady, 1 slow-fast-slow, 2 fast start then eases off, 3 slow start. */
	val pace: Int = 2,
	/** Feathery tendrils, percent of the plate width the ink front is warped by. */
	val tendrilsPercent: Int = 22,
	val raggedPercent: Int = 12,
	val specksPercent: Int = 45,
	/** Softness of the ink front, per mille of the timeline. */
	val edgePerMille: Int = 25,
	val seed: Int = 7,
	val loopFadeMillis: Int = 500
) {
	val edge: Float get() = (edgePerMille / 1000f).coerceAtLeast(0.002f)
}

/** The pace curve applied to linear time. */
fun livingPlateInkPace(progress: Float, pace: Int): Float {
	val p = progress.coerceIn(0f, 1f)
	return when (pace) {
		1 -> if (p < 0.5f) 2f * p * p else 1f - 2f * (1f - p) * (1f - p)
		2 -> 1f - (1f - p) * (1f - p)
		3 -> p * p
		else -> p
	}
}

/** Lab plates are 733 px wide; noise scales are expressed in those units so every size looks alike. */
private const val LabWidth = 733f
/** The soft plate edge hides this share of each side, so it does not count towards the timeline. */
private const val VisibleInset = 0.07f

/**
 * When each pixel of a [width]x[height] mask gains colour (0 first, 1 last). Distance from the centre,
 * warped by fractal noise into lobes and tendrils, roughened, with splatter specks ahead of the front;
 * then equalised by visible area, so progress p colours the share p of the visible plate.
 */
fun livingPlateInkField(width: Int, height: Int, tuning: LivingPlateInkTuning): FloatArray {
	val w = width.coerceAtLeast(1)
	val h = height.coerceAtLeast(1)
	val unit = LabWidth / w
	val labHeight = h * unit
	val seed = tuning.seed * 13.7f
	val warp = tuning.tendrilsPercent / 100f * LabWidth
	val ragged = tuning.raggedPercent / 100f
	val specks = tuning.specksPercent / 100f
	val field = FloatArray(w * h)
	for (y in 0 until h) {
		for (x in 0 until w) {
			val sx = (x + 0.5f) * unit
			val sy = (y + 0.5f) * unit
			val px = sx + warp * (fbm(sx / 120f + seed, sy / 120f, 4) - 0.5f) +
				warp * 0.5f * (fbm(sx / 25f + seed, sy / 25f + 9f, 3) - 0.5f)
			val py = sy + warp * (fbm(sx / 120f + 40f + seed, sy / 120f + 7f, 4) - 0.5f) +
				warp * 0.5f * (fbm(sx / 25f + 3f, sy / 25f + seed, 3) - 0.5f)
			var d = hypot(px - LabWidth / 2f, py - labHeight / 2f) / LabWidth
			d += ragged * (fbm(sx / 9f + seed, sy / 9f, 3) - 0.5f)
			if (specks > 0f && hash(floor(sx / 3f).toInt() + tuning.seed * 101, floor(sy / 3f).toInt()) > 1f - specks * 0.05f) {
				d -= 0.18f * specks
			}
			field[y * w + x] = d
		}
	}
	equaliseByVisibleArea(field, w, h)
	return field
}

/** Rank-by-histogram: same order and shape, but the visible share coloured grows with the timeline. */
private fun equaliseByVisibleArea(field: FloatArray, w: Int, h: Int) {
	var lo = Float.POSITIVE_INFINITY
	var hi = Float.NEGATIVE_INFINITY
	for (v in field) { if (v < lo) lo = v; if (v > hi) hi = v }
	val span = (hi - lo).takeIf { it > 0f } ?: 1f
	val bins = 4096
	val counts = IntArray(bins)
	var visible = 0
	for (y in 0 until h) {
		val vy = (y + 0.5f) / h
		for (x in 0 until w) {
			val vx = (x + 0.5f) / w
			if (vx > VisibleInset && vx < 1f - VisibleInset && vy > VisibleInset && vy < 1f - VisibleInset) {
				counts[bin(field[y * w + x], lo, span, bins)]++
				visible++
			}
		}
	}
	if (visible == 0) {
		for (i in field.indices) field[i] = (field[i] - lo) / span
		return
	}
	val cdf = FloatArray(bins)
	var seen = 0
	for (b in 0 until bins) { seen += counts[b]; cdf[b] = seen.toFloat() / visible }
	for (i in field.indices) field[i] = cdf[bin(field[i], lo, span, bins)]
}

private fun bin(value: Float, lo: Float, span: Float, bins: Int): Int =
	((value - lo) / span * (bins - 1)).toInt().coerceIn(0, bins - 1)

private fun hash(x: Int, y: Int): Float {
	var h = x * 374_761_393 + y * 668_265_263
	h = (h xor (h ushr 13)) * 1_274_126_177
	h = h xor (h ushr 16)
	return (h and 0xFFFFFF) / 16_777_216f
}

private fun smoothNoise(x: Float, y: Float): Float {
	val xi = floor(x).toInt()
	val yi = floor(y).toInt()
	val xf = x - xi
	val yf = y - yi
	val u = xf * xf * (3f - 2f * xf)
	val v = yf * yf * (3f - 2f * yf)
	val a = hash(xi, yi)
	val b = hash(xi + 1, yi)
	val c = hash(xi, yi + 1)
	val d = hash(xi + 1, yi + 1)
	return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

private fun fbm(x: Float, y: Float, octaves: Int): Float {
	var sum = 0f
	var amplitude = 0.5f
	var frequency = 1f
	var norm = 0f
	repeat(octaves) {
		sum += amplitude * smoothNoise(x * frequency, y * frequency)
		norm += amplitude
		amplitude *= 0.5f
		frequency *= 2.03f
	}
	return min(1f, sum / norm)
}
