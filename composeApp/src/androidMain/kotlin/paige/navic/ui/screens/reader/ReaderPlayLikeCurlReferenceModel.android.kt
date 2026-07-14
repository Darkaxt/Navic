package paige.navic.ui.screens.reader

import kotlin.math.abs
import kotlin.math.sin

internal enum class ReaderPlayLikeCurlPageRole {
	Left,
	Front,
	Right
}

internal enum class ReaderPlayLikeCurlActivePage {
	Left,
	Right,
	Current
}

internal enum class ReaderPlayLikeCurlPageChange {
	None,
	Previous,
	Next
}

internal enum class ReaderPlayLikeCurlInterpolator {
	AccelerateDecelerate,
	Decelerate
}

internal enum class ReaderPlayLikeCurlOrientation {
	Portrait,
	Landscape
}

internal data class ReaderPlayLikeCurlSettlement(
	val targetPercent: Int,
	val durationMillis: Long,
	val interpolator: ReaderPlayLikeCurlInterpolator,
	val pageChange: ReaderPlayLikeCurlPageChange
)

internal class ReaderPlayLikeCurlPageState(
	val role: ReaderPlayLikeCurlPageRole,
	val depth: Float,
	internal var curlPosition: Float,
	internal var pageIndex: Int
)

/**
 * State-machine port of PlayLikeCurl's PageRenderer and PageSurfaceView.
 * Rendering and Foliate integration consume this model but do not reinterpret it.
 */
internal class ReaderPlayLikeCurlReferenceModel(
	private val pageCount: Int,
	initialPosition: Int = 0
) {
	val leftPage = ReaderPlayLikeCurlPageState(
		role = ReaderPlayLikeCurlPageRole.Left,
		depth = LeftDepth,
		curlPosition = RightEndpointPosition,
		pageIndex = 0
	)
	val frontPage = ReaderPlayLikeCurlPageState(
		role = ReaderPlayLikeCurlPageRole.Front,
		depth = FrontDepth,
		curlPosition = Grid.toFloat(),
		pageIndex = 0
	)
	val rightPage = ReaderPlayLikeCurlPageState(
		role = ReaderPlayLikeCurlPageRole.Right,
		depth = RightDepth,
		curlPosition = Grid.toFloat(),
		pageIndex = 0
	)

	val drawOrder: List<ReaderPlayLikeCurlPageState> = listOf(leftPage, frontPage, rightPage)

	var activePage: ReaderPlayLikeCurlActivePage = ReaderPlayLikeCurlActivePage.Current
		private set

	var currentPosition: Int = initialPosition
		private set

	private var gestureStartX = 0f
	private var gestureStartCurlPosition = Grid.toFloat()

	init {
		require(pageCount > 0) { "PlayLikeCurl requires at least one page" }
		require(initialPosition in 0 until pageCount) { "Initial page is outside the adapter" }
		resetPages()
		updatePageIdentities()
	}

	fun jumpTo(position: Int) {
		require(position in 0 until pageCount) { "Page is outside the adapter" }
		currentPosition = position
		resetPages()
		updatePageIdentities()
	}

	fun beginGesture(x: Float) {
		gestureStartX = x
		setActivePage(ReaderPlayLikeCurlActivePage.Current)
		gestureStartCurlPosition = activePageState().curlPosition
	}

	fun dragTo(x: Float, width: Float) {
		if (width <= 0f) return
		val delta = x - gestureStartX
		val movedFraction = delta / width
		when {
			delta > 0f -> {
				if (gestureStartCurlPosition >= Grid && canSwipePrevious()) {
					setActivePage(ReaderPlayLikeCurlActivePage.Left)
					gestureStartCurlPosition = activePageState().curlPosition
				}
				val value = gestureStartCurlPosition + movedFraction * Grid
				if (value <= Grid) activePageState().curlPosition = value
			}

			delta < 0f -> {
				val value = (1f - abs(movedFraction)) * Grid - (Grid - gestureStartCurlPosition)
				if (canSwipeNext()) activePageState().curlPosition = value
			}
		}
	}

	fun release(): ReaderPlayLikeCurlSettlement = when (activePage) {
		ReaderPlayLikeCurlActivePage.Current -> settlement(
			targetPercent = LeftEndpointPercent,
			pageChange = ReaderPlayLikeCurlPageChange.None,
			interpolator = ReaderPlayLikeCurlInterpolator.AccelerateDecelerate
		)

		ReaderPlayLikeCurlActivePage.Left,
		ReaderPlayLikeCurlActivePage.Right -> settlement(
			targetPercent = RightEndpointPercent,
			pageChange = ReaderPlayLikeCurlPageChange.None,
			interpolator = ReaderPlayLikeCurlInterpolator.AccelerateDecelerate
		)
	}

	fun flingTowardNext(): ReaderPlayLikeCurlSettlement = if (canSwipeNext()) {
		settlement(
			targetPercent = RightEndpointPercent,
			pageChange = ReaderPlayLikeCurlPageChange.Next,
			interpolator = ReaderPlayLikeCurlInterpolator.Decelerate
		)
	} else {
		release()
	}

	fun flingTowardPrevious(): ReaderPlayLikeCurlSettlement = if (canSwipePrevious()) {
		settlement(
			targetPercent = LeftEndpointPercent,
			pageChange = ReaderPlayLikeCurlPageChange.Previous,
			interpolator = ReaderPlayLikeCurlInterpolator.Decelerate
		)
	} else {
		release()
	}

	fun updateSettlement(valuePercent: Float) {
		activePageState().curlPosition = Grid * valuePercent / 100f
	}

	fun cancelGesture() {
		resetPages()
	}

	fun completeSettlement(settlement: ReaderPlayLikeCurlSettlement) {
		resetPages()
		currentPosition = when (settlement.pageChange) {
			ReaderPlayLikeCurlPageChange.None -> currentPosition
			ReaderPlayLikeCurlPageChange.Previous -> (currentPosition - 1).coerceAtLeast(0)
			ReaderPlayLikeCurlPageChange.Next -> (currentPosition + 1).coerceAtMost(pageCount - 1)
		}
		updatePageIdentities()
	}

	private fun settlement(
		targetPercent: Int,
		pageChange: ReaderPlayLikeCurlPageChange,
		interpolator: ReaderPlayLikeCurlInterpolator
	) = ReaderPlayLikeCurlSettlement(
		targetPercent = targetPercent,
		durationMillis = SettlementDurationMillis,
		interpolator = interpolator,
		pageChange = pageChange
	)

	private fun resetPages() {
		leftPage.curlPosition = RightEndpointPosition
		rightPage.curlPosition = Grid.toFloat()
		frontPage.curlPosition = Grid.toFloat()
		setActivePage(ReaderPlayLikeCurlActivePage.Current)
	}

	private fun updatePageIdentities() {
		leftPage.pageIndex = (currentPosition - 1).coerceAtLeast(0)
		frontPage.pageIndex = currentPosition
		rightPage.pageIndex = (currentPosition + 1).coerceAtMost(pageCount - 1)
	}

	private fun setActivePage(page: ReaderPlayLikeCurlActivePage) {
		activePage = page
	}

	private fun activePageState(): ReaderPlayLikeCurlPageState = when (activePage) {
		ReaderPlayLikeCurlActivePage.Left -> leftPage
		ReaderPlayLikeCurlActivePage.Right -> rightPage
		ReaderPlayLikeCurlActivePage.Current -> frontPage
	}

	private fun canSwipePrevious() = currentPosition > 0

	private fun canSwipeNext() = currentPosition < pageCount - 1

	companion object {
		const val Grid = 25
		const val Radius = 0.18f
		const val LeftEndpointPercent = 100
		const val RightEndpointPercent = -5
		const val SettlementDurationMillis = 300L
		const val LeftDepth = -0.001f
		const val FrontDepth = -0.002f
		const val RightDepth = -0.003f
		const val RightEndpointPosition = Grid * (RightEndpointPercent / 100f)
	}
}

internal class ReaderPlayLikeCurlPageGeometry internal constructor(
	val role: ReaderPlayLikeCurlPageRole,
	internal val bitmapRatio: Float,
	val positions: FloatArray,
	val textureCoordinates: FloatArray,
	val indices: ShortArray
) {
	fun positionY(column: Int, row: Int): Float = positions[vertexOffset(column, row) + 1]

	private fun vertexOffset(column: Int, row: Int): Int =
		3 * (row * (ReaderPlayLikeCurlReferenceModel.Grid + 1) + column)
}

internal object ReaderPlayLikeCurlReferenceGeometry {
	fun createPage(
		role: ReaderPlayLikeCurlPageRole,
		bitmapWidth: Int,
		bitmapHeight: Int,
		orientation: ReaderPlayLikeCurlOrientation
	): ReaderPlayLikeCurlPageGeometry {
		require(bitmapWidth > 0 && bitmapHeight > 0) { "Bitmap dimensions must be positive" }
		val bitmapRatio = when (orientation) {
			ReaderPlayLikeCurlOrientation.Portrait -> bitmapHeight / bitmapWidth.toFloat()
			ReaderPlayLikeCurlOrientation.Landscape -> bitmapWidth / bitmapHeight.toFloat()
		}
		val vertexCount = (ReaderPlayLikeCurlReferenceModel.Grid + 1) *
			(ReaderPlayLikeCurlReferenceModel.Grid + 1)
		val page = ReaderPlayLikeCurlPageGeometry(
			role = role,
			bitmapRatio = bitmapRatio,
			positions = FloatArray(vertexCount * 3),
			textureCoordinates = createTextureCoordinates(),
			indices = createIndices()
		)
		update(
			page = page,
			curlPosition = if (role == ReaderPlayLikeCurlPageRole.Left) {
				ReaderPlayLikeCurlReferenceModel.RightEndpointPosition
			} else {
				ReaderPlayLikeCurlReferenceModel.Grid.toFloat()
			},
			active = false
		)
		return page
	}

	fun update(
		page: ReaderPlayLikeCurlPageGeometry,
		curlPosition: Float,
		active: Boolean
	) {
		val grid = ReaderPlayLikeCurlReferenceModel.Grid
		val heightCorrection = (page.bitmapRatio - 1f) / 2f
		for (row in 0..grid) {
			for (column in 0..grid) {
				val offset = 3 * (row * (grid + 1) + column)
				val normalizedX = column / grid.toFloat()
				page.positions[offset] = when (page.role) {
					ReaderPlayLikeCurlPageRole.Front -> frontX(column, curlPosition)
					ReaderPlayLikeCurlPageRole.Left -> leftX(column, curlPosition)
					ReaderPlayLikeCurlPageRole.Right -> normalizedX
				}
				page.positions[offset + 1] = row / grid.toFloat() * page.bitmapRatio - heightCorrection
				page.positions[offset + 2] = if (active) {
					activeDepth(page.role, column, curlPosition)
				} else {
					depth(page.role)
				}
			}
		}
	}

	fun projectionAspect(width: Int, height: Int): Float {
		require(width > 0 && height > 0) { "Viewport dimensions must be positive" }
		return if (height > width) width / height.toFloat() else height / width.toFloat()
	}

	private fun frontX(column: Int, curlPosition: Float): Float {
		val percentage = 1f - curlPosition / ReaderPlayLikeCurlReferenceModel.Grid
		val radius = resolvedRadius(percentage)
		val movement = if (percentage > 0.05f) percentage - 0.05f else 0f
		return column / ReaderPlayLikeCurlReferenceModel.Grid.toFloat() * (1f - radius) - movement
	}

	private fun leftX(column: Int, curlPosition: Float): Float {
		val percentage = (1f - curlPosition / ReaderPlayLikeCurlReferenceModel.Grid) * 0.75f
		val radius = resolvedRadius(percentage)
		return column / ReaderPlayLikeCurlReferenceModel.Grid.toFloat() * (1f - radius) - percentage
	}

	private fun activeDepth(role: ReaderPlayLikeCurlPageRole, column: Int, curlPosition: Float): Float {
		if (role == ReaderPlayLikeCurlPageRole.Right) return ReaderPlayLikeCurlReferenceModel.RightDepth
		val rawPercentage = 1f - curlPosition / ReaderPlayLikeCurlReferenceModel.Grid
		val percentage = if (role == ReaderPlayLikeCurlPageRole.Left) rawPercentage * 0.75f else rawPercentage
		val radius = resolvedRadius(percentage)
		val waveWidth = if (role == ReaderPlayLikeCurlPageRole.Left) 0.50f else 0.60f
		val delta = ReaderPlayLikeCurlReferenceModel.Grid - curlPosition
		return (
			radius * sin(
				3.14f / (ReaderPlayLikeCurlReferenceModel.Grid * waveWidth) * (column - delta)
			) + radius * 1.1f
		)
	}

	private fun resolvedRadius(percentage: Float): Float =
		if (percentage < 0.20f) ReaderPlayLikeCurlReferenceModel.Radius * percentage * 5f
		else ReaderPlayLikeCurlReferenceModel.Radius

	private fun depth(role: ReaderPlayLikeCurlPageRole): Float = when (role) {
		ReaderPlayLikeCurlPageRole.Left -> ReaderPlayLikeCurlReferenceModel.LeftDepth
		ReaderPlayLikeCurlPageRole.Front -> ReaderPlayLikeCurlReferenceModel.FrontDepth
		ReaderPlayLikeCurlPageRole.Right -> ReaderPlayLikeCurlReferenceModel.RightDepth
	}

	private fun createTextureCoordinates(): FloatArray {
		val grid = ReaderPlayLikeCurlReferenceModel.Grid
		return FloatArray((grid + 1) * (grid + 1) * 2).also { coordinates ->
			for (row in 0..grid) {
				for (column in 0..grid) {
					val offset = 2 * (row * (grid + 1) + column)
					coordinates[offset] = column / grid.toFloat()
					coordinates[offset + 1] = 1f - row / grid.toFloat()
				}
			}
		}
	}

	private fun createIndices(): ShortArray {
		val grid = ReaderPlayLikeCurlReferenceModel.Grid
		return ShortArray(grid * grid * 6).also { indices ->
			for (row in 0 until grid) {
				for (column in 0 until grid) {
					val offset = 6 * (row * grid + column)
					indices[offset] = (row * (grid + 1) + column).toShort()
					indices[offset + 1] = (row * (grid + 1) + column + 1).toShort()
					indices[offset + 2] = ((row + 1) * (grid + 1) + column).toShort()
					indices[offset + 3] = (row * (grid + 1) + column + 1).toShort()
					indices[offset + 4] = ((row + 1) * (grid + 1) + column + 1).toShort()
					indices[offset + 5] = ((row + 1) * (grid + 1) + column).toShort()
				}
			}
		}
	}
}
