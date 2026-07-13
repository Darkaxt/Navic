package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.math.PI
import kotlin.math.sin

internal enum class ReaderPageTurnFixedEdge { Left, Right }

internal class ReaderPageTurnWaveGeometry(
	val columns: Int = DefaultColumns,
	val rows: Int = DefaultRows
) {
	val vertices = FloatArray((columns + 1) * (rows + 1) * CoordinatesPerVertex)
	val textureCoordinates = FloatArray(vertices.size)
	val indices = ShortArray(columns * rows * IndicesPerCell)

	init {
		require(columns > 0) { "Page-turn mesh needs at least one column" }
		require(rows > 0) { "Page-turn mesh needs at least one row" }
		populateIndices()
	}

	fun update(
		leafRect: ReaderPageTurnPixelRect,
		fixedEdge: ReaderPageTurnFixedEdge,
		openness: Float
	) {
		val resolvedOpenness = openness.coerceIn(0f, 1f)
		val direction = if (fixedEdge == ReaderPageTurnFixedEdge.Left) 1f else -1f
		val bindingX = if (fixedEdge == ReaderPageTurnFixedEdge.Left) {
			leafRect.left.toFloat()
		} else {
			leafRect.right.toFloat()
		}
		val width = leafRect.width.toFloat()
		val height = leafRect.height.toFloat()
		val waveEnvelope = 4f * resolvedOpenness * (1f - resolvedOpenness)

		for (row in 0..rows) {
			val vertical = row / rows.toFloat()
			val y = leafRect.top + height * vertical
			val verticalWave = sin(PI * vertical).toFloat()
			for (column in 0..columns) {
				val distanceFromBinding = column / columns.toFloat()
				val textureX = bindingX + direction * width * distanceFromBinding
				val baseX = bindingX + direction * width * distanceFromBinding * resolvedOpenness
				val waveX = direction * width * MaxWaveFraction * waveEnvelope * distanceFromBinding * verticalWave
				val coordinateIndex = coordinateIndex(column, row)
				vertices[coordinateIndex] = baseX + waveX
				vertices[coordinateIndex + 1] = y
				textureCoordinates[coordinateIndex] = textureX
				textureCoordinates[coordinateIndex + 1] = y
			}
		}
	}

	fun vertexX(column: Int, row: Int): Float = vertices[coordinateIndex(column, row)]
	fun vertexY(column: Int, row: Int): Float = vertices[coordinateIndex(column, row) + 1]

	private fun coordinateIndex(column: Int, row: Int): Int {
		require(column in 0..columns)
		require(row in 0..rows)
		return (row * (columns + 1) + column) * CoordinatesPerVertex
	}

	private fun populateIndices() {
		var index = 0
		for (row in 0 until rows) {
			for (column in 0 until columns) {
				val topLeft = (row * (columns + 1) + column).toShort()
				val topRight = (topLeft + 1).toShort()
				val bottomLeft = (topLeft + columns + 1).toShort()
				val bottomRight = (bottomLeft + 1).toShort()
				indices[index++] = topLeft
				indices[index++] = bottomLeft
				indices[index++] = topRight
				indices[index++] = topRight
				indices[index++] = bottomLeft
				indices[index++] = bottomRight
			}
		}
	}

	companion object {
		const val MaxWaveFraction = 0.055f
		private const val DefaultColumns = 16
		private const val DefaultRows = 8
		private const val CoordinatesPerVertex = 2
		private const val IndicesPerCell = 6
	}
}
