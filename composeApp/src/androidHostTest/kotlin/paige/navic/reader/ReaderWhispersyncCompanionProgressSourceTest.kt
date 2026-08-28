package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.ui.screens.reader.readerWhispersyncCueMapReportSurface

class ReaderWhispersyncCompanionProgressSourceTest {
	@Test
	fun readerScreenPersistsCompanionProgressOnlyWithDeliveredWhispersyncSeek() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"audioSeekTarget = step.whispersyncAudioSeekTarget",
			message = "ReaderScreen must persist companion audio progress only from the exact seek delivered by the current coordinator step."
		)
		assertFalse(
			readerScreen.contains("coordinator.controller.state.whispersync.audioSeekTarget"),
			message = "Pending, unconfirmed overlay seeks must not leak into persisted companion progress."
		)
	}

	@Test
	fun readerScreenLoadsWhispersyncAudiobookPlanWithCompanionAwareResumeProgress() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"binderyAudiobookResumeProgressForWhispersyncReader(",
			message = "Whispersync reader sessions must use the same newest direct-or-companion resume policy as the audiobook player, not stale direct audiobook progress only."
		)
	}

	@Test
	fun readerScreenSurfacesWhispersyncLoadFailuresThroughControllerStatus() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"coordinator.dispatch { reportWhispersyncLoadFailure(",
			message = "Whispersync sidecar or paired-audiobook load failures must surface through controller-owned native status, not only logs."
		)
	}

	@Test
	fun productionCueMapReportRetainsCompleteLabelInOneNonWrappingLine() {
		val digest = "5f04c2a19e7d"
		val cueMap = ReaderWhispersyncCueMapState()
			.toggled(digest)
			.rendered((0 until 27).toList(), digest)
			.requested(
				sourceOrdinal = 26,
				revisionDigest = digest,
				audioResource = "Audio/chapter01.m4b",
				audioTrackIndex = 0,
				positionMs = 26_000L
			)
			.transportAcknowledged(
				sourceOrdinal = 26,
				revisionDigest = digest,
				audioResource = "Audio/chapter01.m4b",
				audioTrackIndex = 0,
				positionMs = 26_000L
			)
			.audioActive(26, digest)
			.renderedHighlight(26, digest)
		val diagnostic = cueMap.productionDiagnosticSurface(digest)
		val reportSurface = assertNotNull(
			readerWhispersyncCueMapReportSurface(
				enabled = true,
				diagnosticLabel = diagnostic.label
			)
		)

		assertEquals(ReaderWhispersyncCueMapTransitionLimit, diagnostic.tokens.size)
		assertEquals(diagnostic.label, reportSurface.label)
		assertTrue("g1:26:r:seek" in reportSurface.label)
		assertTrue("g1:26:a:project" in reportSurface.label)
		assertTrue("g1:26:h:render" in reportSurface.label)
		assertTrue(reportSurface.label.endsWith(diagnostic.tokens.last()))
		assertEquals(1, reportSurface.maxLines)
		assertFalse(reportSurface.softWrap)
	}

	@Test
	fun productionReaderPlacesOptInCueMapBesideExistingWhispersyncControl() {
		val root = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val control = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt"
		).readText()

		assertContains(root, "KomikkuWhispersyncCueMapControl(")
		assertContains(root, "cueMapAvailable = controllerState.whispersync.available")
		assertContains(root, "onToggleWhispersyncCueMap")
		assertContains(root, "productionDiagnosticSurface(")
		assertContains(root, "diagnosticLabel = cueMapDiagnosticSurface.label")
		assertContains(control, "internal fun KomikkuWhispersyncCueMapControl(")
		assertContains(control, "diagnosticLabel: String")
		assertContains(control, "enabled: Boolean")
		assertContains(control, "readerWhispersyncCueMapReportSurface(")
		assertContains(control, "maxLines = reportSurface.maxLines")
		assertContains(control, "softWrap = reportSurface.softWrap")
		assertContains(control, "overflow = TextOverflow.Ellipsis")
		val cueMapControlSource = control
			.substringAfter("internal fun KomikkuWhispersyncCueMapControl(")
			.substringBefore("internal fun KomikkuWhispersyncStatusBadge(")
		assertFalse(cueMapControlSource.contains("TextOverflow.Visible"))
		assertFalse(root.contains("ReaderDev"))
		assertFalse(control.contains("ReaderDev"))
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
