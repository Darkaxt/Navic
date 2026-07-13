package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPageTurnWaveGeometryTest {
	private val leaf = ReaderPageTurnPixelRect(left = 300, top = 20, right = 900, bottom = 820)

	@Test
	fun bindingVerticesNeverMove() {
		val geometry = ReaderPageTurnWaveGeometry(columns = 16, rows = 8)
		geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.47f)

		for (row in 0..geometry.rows) {
			assertEquals(leaf.left.toFloat(), geometry.vertexX(column = 0, row = row))
		}
	}

	@Test
	fun outerEdgeFollowsNormalizedOpenness() {
		val geometry = ReaderPageTurnWaveGeometry(columns = 16, rows = 8)
		geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.5f)

		assertEquals(
			leaf.left + leaf.width * 0.5f,
			geometry.vertexX(column = geometry.columns, row = 0)
		)
	}

	@Test
	fun intermediateStateHasVisibleCurvatureButEndpointsDoNot() {
		val geometry = ReaderPageTurnWaveGeometry(columns = 16, rows = 8)
		geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.5f)
		val intermediateDelta = geometry.vertexX(geometry.columns, geometry.rows / 2) -
			geometry.vertexX(geometry.columns, 0)
		assertTrue(abs(intermediateDelta) > 1f)

		for (openness in listOf(0f, 1f)) {
			geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness)
			for (column in 0..geometry.columns) {
				assertEquals(
					geometry.vertexX(column, 0),
					geometry.vertexX(column, geometry.rows / 2),
					absoluteTolerance = 0.001f
				)
			}
		}
	}

	@Test
	fun intermediateCompressionIsLimitedToTheMovingEdgeBand() {
		val geometry = ReaderPageTurnWaveGeometry(columns = 20, rows = 8)
		geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.6f)

		val interiorColumn = 4
		val interiorTextureX = leaf.left + leaf.width * (interiorColumn / geometry.columns.toFloat())
		assertEquals(
			interiorTextureX,
			geometry.vertexX(interiorColumn, geometry.rows / 2),
			absoluteTolerance = 0.001f,
			message = "The stable page interior must remain at 1:1 scale."
		)

		val edgeBandColumn = 10
		val edgeBandTextureX = leaf.left + leaf.width * (edgeBandColumn / geometry.columns.toFloat())
		assertTrue(
			geometry.vertexX(edgeBandColumn, geometry.rows / 2) < edgeBandTextureX - 1f,
			"Only the strip approaching the moving edge should compress."
		)
	}

	@Test
	fun retiredTextureColumnsCollapseAtTheMovingBoundaryInsteadOfCompressingAcrossTheLeaf() {
		val geometry = ReaderPageTurnWaveGeometry(columns = 20, rows = 8)
		geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.5f)

		val movingEdgeX = leaf.left + leaf.width * 0.5f
		val collapsedFromColumn = 13
		for (column in collapsedFromColumn..geometry.columns) {
			assertEquals(
				movingEdgeX,
				geometry.vertexX(column, 0),
				absoluteTolerance = 0.001f
			)
		}
		assertEquals(movingEdgeX, geometry.vertexX(geometry.columns, 0), absoluteTolerance = 0.001f)
	}

	@Test
	fun rightFixedGeometryMirrorsLeftFixedGeometry() {
		val leftFixed = ReaderPageTurnWaveGeometry(columns = 16, rows = 8)
		val rightFixed = ReaderPageTurnWaveGeometry(columns = 16, rows = 8)
		leftFixed.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.42f)
		rightFixed.update(leaf, ReaderPageTurnFixedEdge.Right, openness = 0.42f)

		for (column in 0..leftFixed.columns) {
			for (row in 0..leftFixed.rows) {
				val mirrored = leaf.left + leaf.right - leftFixed.vertexX(column, row)
				assertEquals(
					mirrored,
					rightFixed.vertexX(column, row),
					absoluteTolerance = 0.001f
				)
			}
		}
	}

	@Test
	fun updatesReusePreallocatedBuffersAndStayBounded() {
		val geometry = ReaderPageTurnWaveGeometry(columns = 16, rows = 8)
		val vertices = geometry.vertices
		val textureCoordinates = geometry.textureCoordinates
		val indices = geometry.indices

		geometry.update(leaf, ReaderPageTurnFixedEdge.Left, openness = 0.31f)
		geometry.update(leaf, ReaderPageTurnFixedEdge.Right, openness = 0.73f)

		assertSame(vertices, geometry.vertices)
		assertSame(textureCoordinates, geometry.textureCoordinates)
		assertSame(indices, geometry.indices)
		val allowance = leaf.width * ReaderPageTurnWaveGeometry.MaxWaveFraction
		for (index in geometry.vertices.indices step 2) {
			assertTrue(geometry.vertices[index] in leaf.left - allowance..leaf.right + allowance)
			assertTrue(geometry.vertices[index + 1] in leaf.top.toFloat()..leaf.bottom.toFloat())
		}
	}
}
