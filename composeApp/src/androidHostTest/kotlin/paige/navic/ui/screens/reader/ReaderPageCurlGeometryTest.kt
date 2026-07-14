package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageTurnPhysicalDirection

class ReaderPageCurlGeometryTest {
	@Test
	fun referenceMeshUsesLockedPlayLikeCurlDimensions() {
		val mesh = ReaderPageCurlGeometry.createReferenceMesh()

		assertEquals(25, ReaderPageCurlGeometry.Grid)
		assertEquals(0.18f, ReaderPageCurlGeometry.Radius)
		assertEquals(26 * 26, mesh.vertexCount)
		assertEquals(mesh.vertexCount * 3, mesh.positions.size)
		assertEquals(mesh.vertexCount * 2, mesh.textureCoordinates.size)
		assertEquals(25 * 25 * 6, mesh.indices.size)
	}

	@Test
	fun referenceMeshUsesStableUvAndTriangleOrder() {
		val mesh = ReaderPageCurlGeometry.createReferenceMesh()

		assertEquals(0f, mesh.textureU(column = 0, row = 0))
		assertEquals(1f, mesh.textureV(column = 0, row = 0))
		assertEquals(1f, mesh.textureU(column = ReaderPageCurlGeometry.Grid, row = ReaderPageCurlGeometry.Grid))
		assertEquals(0f, mesh.textureV(column = ReaderPageCurlGeometry.Grid, row = ReaderPageCurlGeometry.Grid))
		assertTrue(mesh.textureCoordinates.all { it in 0f..1f })
		assertContentEquals(
			shortArrayOf(0, 1, 26, 1, 27, 26),
			mesh.indices.copyOfRange(0, 6)
		)
		assertContentEquals(
			shortArrayOf(648, 649, 674, 649, 675, 674),
			mesh.indices.copyOfRange(mesh.indices.size - 6, mesh.indices.size)
		)
	}

	@Test
	fun directionEndpointsAndProgressClampingMatchTheReference() {
		val forwardStart = ReaderPageCurlGeometry.forward(progress = 0f)
		val forwardEnd = ReaderPageCurlGeometry.forward(progress = 1f)
		val backwardStart = ReaderPageCurlGeometry.backward(progress = 0f)
		val backwardEnd = ReaderPageCurlGeometry.backward(progress = 1f)

		assertEquals(ReaderPageCurlGeometry.LeftEndpoint, forwardStart.curlPosition)
		assertEquals(ReaderPageCurlGeometry.RightEndpoint, forwardEnd.curlPosition)
		assertEquals(ReaderPageCurlGeometry.RightEndpoint, backwardStart.curlPosition)
		assertEquals(ReaderPageCurlGeometry.LeftEndpoint, backwardEnd.curlPosition)
		assertContentEquals(forwardStart.positions, ReaderPageCurlGeometry.forward(progress = -1f).positions)
		assertContentEquals(forwardEnd.positions, ReaderPageCurlGeometry.forward(progress = 2f).positions)
		assertContentEquals(backwardStart.positions, ReaderPageCurlGeometry.backward(progress = -1f).positions)
		assertContentEquals(backwardEnd.positions, ReaderPageCurlGeometry.backward(progress = 2f).positions)
	}

	@Test
	fun geometryUpdatesReuseTheProvidedMeshAndRemainFinite() {
		val mesh = ReaderPageCurlGeometry.createReferenceMesh()
		val positions = mesh.positions
		val textureCoordinates = mesh.textureCoordinates
		val indices = mesh.indices

		for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
			assertSame(mesh, ReaderPageCurlGeometry.forward(progress, mesh))
			assertTrue(mesh.positions.all(Float::isFinite))
			assertSame(positions, mesh.positions)
			assertSame(textureCoordinates, mesh.textureCoordinates)
			assertSame(indices, mesh.indices)
			assertSame(mesh, ReaderPageCurlGeometry.backward(progress, mesh))
			assertTrue(mesh.positions.all(Float::isFinite))
		}
	}

	@Test
	fun backwardGeometryIsNotForwardGeometryPlayedInReverse() {
		val forward = ReaderPageCurlGeometry.forward(progress = 0.5f)
		val backward = ReaderPageCurlGeometry.backward(progress = 0.5f)

		assertFalse(forward.positions.contentEquals(backward.positions))
	}

	@Test
	fun landscapeFoldReflectsAtTheBindingWithoutCrossingTheSplitter() {
		val towardLeft = ReaderPageCurlGeometry.forward(progress = 0.75f)
		val rawTowardLeft = towardLeft.positions.copyOf()
		val crossedBindingIndex = rawTowardLeft.indices.step(3).first { rawTowardLeft[it] < 0f }

		ReaderPageCurlLeafProjection.apply(
			mesh = towardLeft,
			direction = ReaderPageTurnPhysicalDirection.TowardLeft
		)

		assertTrue(towardLeft.positions.indices.step(3).all { towardLeft.positions[it] in 0f..1f })
		assertEquals(
			-rawTowardLeft[crossedBindingIndex],
			towardLeft.positions[crossedBindingIndex]
		)

		assertEquals(
			0.82f,
			ReaderPageCurlLeafProjection.projectX(
				positionX = 1.18f,
				direction = ReaderPageTurnPhysicalDirection.TowardRight
			),
			absoluteTolerance = Tolerance
		)
	}

	@Test
	fun forwardGeometryMatchesAuditedJavaSamples() {
		assertParity(
			update = ReaderPageCurlGeometry::forward,
			fixtures = listOf(
				Fixture(0f, 0.48f, 0.52f, 0f),
				Fixture(0.25f, 0.18110001f, 0.52f, 0.36142224f),
				Fixture(0.5f, -0.08139997f, 0.52f, 0.15600075f),
				Fixture(0.75f, -0.34390002f, 0.52f, 0.018133067f),
				Fixture(1f, -0.60639995f, 0.52f, 0.16957285f)
			)
		)
	}

	@Test
	fun backwardGeometryMatchesAuditedJavaSamples() {
		assertParity(
			update = ReaderPageCurlGeometry::backward,
			fixtures = listOf(
				Fixture(0f, -0.39390004f, 0.52f, 0.27434444f),
				Fixture(0.25f, -0.19702499f, 0.52f, 0.029557837f),
				Fixture(0.5f, -0.00015001536f, 0.52f, 0.14780639f),
				Fixture(0.75f, 0.19807498f, 0.52f, 0.3683874f),
				Fixture(1f, 0.48f, 0.52f, 0f)
			)
		)
	}

	private fun assertParity(
		update: (Float, ReaderPageCurlMesh) -> ReaderPageCurlMesh,
		fixtures: List<Fixture>
	) {
		val mesh = ReaderPageCurlGeometry.createReferenceMesh()
		for (fixture in fixtures) {
			update(fixture.progress, mesh)
			assertEquals(fixture.x, mesh.positionX(SampleColumn, SampleRow), absoluteTolerance = Tolerance)
			assertEquals(fixture.y, mesh.positionY(SampleColumn, SampleRow), absoluteTolerance = Tolerance)
			assertEquals(fixture.z, mesh.positionZ(SampleColumn, SampleRow), absoluteTolerance = Tolerance)
			assertEquals(SampleColumn / ReaderPageCurlGeometry.Grid.toFloat(), mesh.textureU(SampleColumn, SampleRow))
			assertEquals(1f - SampleRow / ReaderPageCurlGeometry.Grid.toFloat(), mesh.textureV(SampleColumn, SampleRow))
		}
	}

	private data class Fixture(
		val progress: Float,
		val x: Float,
		val y: Float,
		val z: Float
	)

	private companion object {
		const val SampleColumn = 12
		const val SampleRow = 13
		const val Tolerance = 0.0001f
	}
}
