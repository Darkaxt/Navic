package paige.navic.reader

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

data class ReaderPageTurnPoint(val x: Float, val y: Float)

data class ReaderPageTurnSegment(val first: ReaderPageTurnPoint, val second: ReaderPageTurnPoint)

/**
 * A planar page fold derived from the exact outer-edge grab and live pointer.
 * Points on the folded side are reflected across the perpendicular bisector of that gesture.
 */
class ReaderPageTurnEdgeFoldGeometry(
	private val width: Float,
	private val height: Float,
	progress: Float,
	private val direction: ReaderPageTurnPhysicalDirection,
	edgeOriginY: Float,
	pointerY: Float
) {
	private val progress = progress.coerceIn(0f, MaxTurnProgress)
	private val edgeOriginY = edgeOriginY.coerceIn(0f, height)
	private val pointerY = pointerY.coerceIn(0f, height)
	private val freeEdgeX = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) width else 0f
	private val bindingEdgeX = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) 0f else width
	private val inwardSign = if (direction == ReaderPageTurnPhysicalDirection.TowardLeft) -1f else 1f
	private val pointerX = freeEdgeX + inwardSign * width * this.progress
	private val midpointX = (freeEdgeX + pointerX) * 0.5f
	private val midpointY = (this.edgeOriginY + this.pointerY) * 0.5f
	private val normalX = freeEdgeX - pointerX
	private val normalY = this.edgeOriginY - this.pointerY
	private val normalLengthSquared = normalX * normalX + normalY * normalY
	private val normalLength = sqrt(normalLengthSquared)
	private val unitNormalX = if (normalLength > Epsilon) normalX / normalLength else 0f
	private val unitNormalY = if (normalLength > Epsilon) normalY / normalLength else 0f
	private val unitTangentX = -unitNormalY
	private val unitTangentY = unitNormalX
	private val curlEnvelope = sin(this.progress / MaxTurnProgress * PI).toFloat().coerceAtLeast(0f)
	private val curlRadius = min(
		width * CurlRadiusFraction * curlEnvelope,
		normalLength * CurlContactSafety / PI.toFloat()
	)
	private val curlBandHalfLength = PI.toFloat() * curlRadius * 0.5f

	init {
		require(width > 0f) { "Page width must be positive." }
		require(height > 0f) { "Page height must be positive." }
	}

	fun foldBoundaryX(y: Float): Float {
		if (normalLengthSquared <= Epsilon || abs(normalX) <= Epsilon) return freeEdgeX
		return midpointX - (y - midpointY) * normalY / normalX
	}

	fun map(point: ReaderPageTurnPoint): ReaderPageTurnPoint {
		val mapped = FloatArray(2)
		mapInto(point.x, point.y, mapped, 0)
		return ReaderPageTurnPoint(mapped[0], mapped[1])
	}

	fun mapInto(x: Float, y: Float, destination: FloatArray, offset: Int) {
		require(offset >= 0 && offset + 1 < destination.size) { "Destination must contain two writable values." }
		if (progress <= 0f || x == bindingEdgeX || normalLengthSquared <= Epsilon) {
			write(destination, offset, x, y)
			return
		}
		val signedDistance = signedDistanceNumerator(x, y) / normalLength
		if (curlRadius <= Epsilon || signedDistance <= -curlBandHalfLength || signedDistance >= curlBandHalfLength) {
			if (signedDistance <= Epsilon) {
				write(destination, offset, x, y)
				return
			}
			val reflectionScale = 2f * signedDistance / normalLength
			write(
				destination,
				offset,
				x - reflectionScale * normalX,
				y - reflectionScale * normalY
			)
			return
		}
		val relativeX = x - midpointX
		val relativeY = y - midpointY
		val tangentDistance = relativeX * unitTangentX + relativeY * unitTangentY
		val curlAngle = (signedDistance + curlBandHalfLength) / curlRadius
		val projectedNormalDistance = -curlBandHalfLength + curlRadius * sin(curlAngle)
		write(
			destination,
			offset,
			midpointX + unitTangentX * tangentDistance + unitNormalX * projectedNormalDistance,
			midpointY + unitTangentY * tangentDistance + unitNormalY * projectedNormalDistance
		)
	}

	fun visibleCreaseSegment(): ReaderPageTurnSegment? {
		val source = foldBoundarySegment() ?: return null
		return ReaderPageTurnSegment(map(source.first), map(source.second))
	}

	fun foldBoundarySegment(): ReaderPageTurnSegment? {
		if (normalLengthSquared <= Epsilon) return null
		val intersections = mutableListOf<ReaderPageTurnPoint>()
		addIfInside(intersections, ReaderPageTurnPoint(foldBoundaryX(0f), 0f))
		addIfInside(intersections, ReaderPageTurnPoint(foldBoundaryX(height), height))
		if (abs(normalY) > Epsilon) {
			addIfInside(intersections, ReaderPageTurnPoint(0f, boundaryYAt(0f)))
			addIfInside(intersections, ReaderPageTurnPoint(width, boundaryYAt(width)))
		}
		if (intersections.size < 2) return null
		var first = intersections[0]
		var second = intersections[1]
		var farthest = squaredDistance(first, second)
		for (firstIndex in intersections.indices) {
			for (secondIndex in firstIndex + 1 until intersections.size) {
				val distance = squaredDistance(intersections[firstIndex], intersections[secondIndex])
				if (distance > farthest) {
					farthest = distance
					first = intersections[firstIndex]
					second = intersections[secondIndex]
				}
			}
		}
		return ReaderPageTurnSegment(first, second)
	}

	fun foldedRegionOutline(): List<ReaderPageTurnPoint> {
		if (normalLengthSquared <= Epsilon) return emptyList()
		val page = listOf(
			ReaderPageTurnPoint(0f, 0f),
			ReaderPageTurnPoint(width, 0f),
			ReaderPageTurnPoint(width, height),
			ReaderPageTurnPoint(0f, height)
		)
		val foldedSource = clipToFoldedHalfPlane(page)
		return foldedSource.map(::map)
	}

	private fun clipToFoldedHalfPlane(page: List<ReaderPageTurnPoint>): List<ReaderPageTurnPoint> {
		val clipped = mutableListOf<ReaderPageTurnPoint>()
		var previous = page.last()
		var previousDistance = signedDistanceNumerator(previous.x, previous.y)
		for (current in page) {
			val currentDistance = signedDistanceNumerator(current.x, current.y)
			val previousInside = previousDistance >= -Epsilon
			val currentInside = currentDistance >= -Epsilon
			if (currentInside != previousInside) {
				val denominator = previousDistance - currentDistance
				if (abs(denominator) > Epsilon) {
					val amount = previousDistance / denominator
					clipped += ReaderPageTurnPoint(
						x = previous.x + (current.x - previous.x) * amount,
						y = previous.y + (current.y - previous.y) * amount
					)
				}
			}
			if (currentInside) clipped += current
			previous = current
			previousDistance = currentDistance
		}
		return clipped
	}

	private fun signedDistanceNumerator(x: Float, y: Float): Float =
		(x - midpointX) * normalX + (y - midpointY) * normalY

	private fun boundaryYAt(x: Float): Float = midpointY - (x - midpointX) * normalX / normalY

	private fun addIfInside(points: MutableList<ReaderPageTurnPoint>, point: ReaderPageTurnPoint) {
		if (point.x !in -Epsilon..width + Epsilon || point.y !in -Epsilon..height + Epsilon) return
		val bounded = ReaderPageTurnPoint(point.x.coerceIn(0f, width), point.y.coerceIn(0f, height))
		if (points.none { squaredDistance(it, bounded) <= Epsilon }) points += bounded
	}

	private fun write(destination: FloatArray, offset: Int, x: Float, y: Float) {
		destination[offset] = x
		destination[offset + 1] = y
	}

	private fun squaredDistance(first: ReaderPageTurnPoint, second: ReaderPageTurnPoint): Float {
		val dx = first.x - second.x
		val dy = first.y - second.y
		return dx * dx + dy * dy
	}

	private companion object {
			const val MaxTurnProgress = 2f
		const val CurlRadiusFraction = 0.075f
		const val CurlContactSafety = 0.92f
		const val Epsilon = 0.0001f
	}
}
