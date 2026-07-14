package paige.navic.ui.screens.reader

import kotlin.math.sin
import paige.navic.reader.ReaderPageTurnPhysicalDirection

internal class ReaderPageCurlMesh internal constructor(
	val grid: Int,
	val radius: Float
) {
	val vertexCount: Int = (grid + 1) * (grid + 1)
	val positions: FloatArray = FloatArray(vertexCount * PositionCoordinates)
	val textureCoordinates: FloatArray = FloatArray(vertexCount * TextureCoordinates)
	val indices: ShortArray = ShortArray(grid * grid * IndicesPerCell)
	var curlPosition: Float = ReaderPageCurlGeometry.LeftEndpoint
		internal set

	init {
		populateTextureCoordinates()
		populateIndices()
	}

	fun positionX(column: Int, row: Int): Float = positions[positionIndex(column, row)]
	fun positionY(column: Int, row: Int): Float = positions[positionIndex(column, row) + 1]
	fun positionZ(column: Int, row: Int): Float = positions[positionIndex(column, row) + 2]
	fun textureU(column: Int, row: Int): Float = textureCoordinates[textureIndex(column, row)]
	fun textureV(column: Int, row: Int): Float = textureCoordinates[textureIndex(column, row) + 1]

	internal fun positionIndex(column: Int, row: Int): Int {
		require(column in 0..grid)
		require(row in 0..grid)
		return (row * (grid + 1) + column) * PositionCoordinates
	}

	private fun textureIndex(column: Int, row: Int): Int {
		require(column in 0..grid)
		require(row in 0..grid)
		return (row * (grid + 1) + column) * TextureCoordinates
	}

	private fun populateTextureCoordinates() {
		for (row in 0..grid) {
			for (column in 0..grid) {
				val index = textureIndex(column, row)
				textureCoordinates[index] = column / grid.toFloat()
				textureCoordinates[index + 1] = 1f - row / grid.toFloat()
			}
		}
	}

	private fun populateIndices() {
		var index = 0
		for (row in 0 until grid) {
			for (column in 0 until grid) {
				val topLeft = (row * (grid + 1) + column).toShort()
				val topRight = (topLeft + 1).toShort()
				val bottomLeft = ((row + 1) * (grid + 1) + column).toShort()
				val bottomRight = (bottomLeft + 1).toShort()
				indices[index++] = topLeft
				indices[index++] = topRight
				indices[index++] = bottomLeft
				indices[index++] = topRight
				indices[index++] = bottomRight
				indices[index++] = bottomLeft
			}
		}
	}

	private companion object {
		const val PositionCoordinates = 3
		const val TextureCoordinates = 2
		const val IndicesPerCell = 6
	}
}

/** Faithful, normalized port of the audited PlayLikeCurl page equations. */
internal object ReaderPageCurlGeometry {
	const val Grid = 25
	const val Radius = 0.18f
	const val RightEndpoint = -1.25f
	const val LeftEndpoint = 25f

	private const val RightDepth = -0.003f
	private const val ReferencePi = 3.14f

	fun createReferenceMesh(): ReaderPageCurlMesh = ReaderPageCurlMesh(
		grid = Grid,
		radius = Radius
	).also(::stationary)

	fun forward(
		progress: Float,
		target: ReaderPageCurlMesh = createReferenceMesh()
	): ReaderPageCurlMesh {
		requireReferenceMesh(target)
		val resolvedProgress = progress.coerceIn(0f, 1f)
		val curlPosition = LeftEndpoint + (RightEndpoint - LeftEndpoint) * resolvedProgress
		val percentage = 1f - curlPosition / Grid
		val deltaX = Grid - curlPosition
		var calculatedRadius = Radius
		var movementX = 0f
		if (percentage < 0.20f) {
			calculatedRadius = Radius * percentage * 5f
		}
		if (percentage > 0.05f) {
			movementX = percentage - 0.05f
		}

		target.curlPosition = curlPosition
		updateActiveMesh(
			target = target,
			calculatedRadius = calculatedRadius,
			movementX = movementX,
			deltaX = deltaX,
			waveWidth = Grid * 0.60f
		)
		return target
	}

	fun backward(
		progress: Float,
		target: ReaderPageCurlMesh = createReferenceMesh()
	): ReaderPageCurlMesh {
		requireReferenceMesh(target)
		val resolvedProgress = progress.coerceIn(0f, 1f)
		val curlPosition = RightEndpoint + (LeftEndpoint - RightEndpoint) * resolvedProgress
		val percentage = (1f - curlPosition / Grid) * 0.75f
		val deltaX = Grid - curlPosition
		var calculatedRadius = Radius
		if (percentage < 0.20f) {
			calculatedRadius = Radius * percentage * 5f
		}

		target.curlPosition = curlPosition
		updateActiveMesh(
			target = target,
			calculatedRadius = calculatedRadius,
			movementX = percentage,
			deltaX = deltaX,
			waveWidth = Grid * 0.50f
		)
		return target
	}

	private fun stationary(target: ReaderPageCurlMesh) {
		for (row in 0..Grid) {
			for (column in 0..Grid) {
				val index = target.positionIndex(column, row)
				target.positions[index] = column / Grid.toFloat()
				target.positions[index + 1] = row / Grid.toFloat()
				target.positions[index + 2] = RightDepth
			}
		}
	}

	private fun updateActiveMesh(
		target: ReaderPageCurlMesh,
		calculatedRadius: Float,
		movementX: Float,
		deltaX: Float,
		waveWidth: Float
	) {
		val widthRatio = 1f - calculatedRadius
		for (row in 0..Grid) {
			for (column in 0..Grid) {
				val index = target.positionIndex(column, row)
				val wave = sin(ReferencePi / waveWidth * (column - deltaX))
				target.positions[index] = column / Grid.toFloat() * widthRatio - movementX
				target.positions[index + 1] = row / Grid.toFloat()
				target.positions[index + 2] = calculatedRadius * wave + calculatedRadius * 1.1f
			}
		}
	}

	private fun requireReferenceMesh(target: ReaderPageCurlMesh) {
		require(target.grid == Grid) { "PlayLikeCurl geometry requires a $Grid x $Grid grid" }
		require(target.radius == Radius) { "PlayLikeCurl geometry requires radius $Radius" }
	}
}

/** Keeps a spread curl on its own leaf after it reaches the center binding. */
internal object ReaderPageCurlLeafProjection {
	fun apply(mesh: ReaderPageCurlMesh, direction: ReaderPageTurnPhysicalDirection) {
		for (index in mesh.positions.indices step 3) {
			mesh.positions[index] = projectX(mesh.positions[index], direction)
		}
	}

	fun projectX(
		positionX: Float,
		direction: ReaderPageTurnPhysicalDirection
	): Float = when (direction) {
		ReaderPageTurnPhysicalDirection.TowardLeft ->
			if (positionX < 0f) -positionX else positionX

		ReaderPageTurnPhysicalDirection.TowardRight ->
			if (positionX > 1f) 2f - positionX else positionX
	}
}
