package paige.navic.reader

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class BinderyWhispersyncSchemaContractTest {
	private val root = sequence {
		var candidate = Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("androidApp/build.gradle.kts").exists()
	}

	@Test
	fun whispersyncSpecAndPlanTrackCurrentBinderySchemaAuthority() {
		val spec = root.resolve("docs/superpowers/specs/2026-06-18-whispersync-design.md").readText()
		val plan = root.resolve("docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md").readText()

		assertTrue(
			spec.contains("Bindery API Compatibility As Of 2026-06-29") &&
				spec.contains("Last updated: 2026-06-29") &&
				spec.contains("navic-opds-api-schema.md"),
			"The Whispersync spec must name the current 2026-06-29 Bindery schema authority before behavior work continues."
		)
		assertTrue(
			plan.contains("Stage 5C.1: Bindery Whispersync Schema Drift Guard") &&
				plan.contains("last updated 2026-06-29") &&
				plan.contains("schema/model/route correctness gate"),
			"The staged plan must keep schema drift as an explicit Whispersync gate, not a release-time surprise."
		)
	}

	@Test
	fun binderyWhispersyncSchemaFieldsHaveParserAndBehaviorCoverage() {
		val binderyModels = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyModels.kt")
			.readText()
		val binderyMappings = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/data/remote/bindery/BinderyDtoMapping.kt")
			.readText()
		val bookSyncJsonTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyBookSyncJsonTest.kt")
			.readText()
		val catalogJsonTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryCatalogJsonTest.kt")
			.readText()
		val resourceJsonTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryResourceJsonTest.kt")
			.readText()
		val progressJsonTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryProgressCacheTest.kt")
			.readText()
		val sidecarModels = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/WhispersyncModels.kt")
			.readText()
		val sidecarParserTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/reader/WhispersyncTimelineParserTest.kt")
			.readText()
		val bookVersionPolicyTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicyTest.kt")
			.readText()
		val catalogDisplayTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogDisplayPolicyTest.kt")
			.readText()
		val progressSyncTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderProgressSyncTest.kt")
			.readText()

		assertContainsAll(
			binderyModels,
			"BinderyBookSync model",
			"whispersyncStatus",
			"syncPairCounts",
			"syncPairs",
			"ebookBookFileId",
			"audiobookBookFileId",
			"artifactHref",
			"audioCoverage",
			"ebookCoverage",
			"lastJob",
			"progressPercent"
		)
		assertContainsAll(
			bookSyncJsonTest + catalogJsonTest + bookVersionPolicyTest + catalogDisplayTest,
			"exact ready-pair coverage",
			"readyWhispersyncPairOnlyRequiresReadyStatusAndArtifactHref",
			"bookCardsExposeWhispersyncBadgeOnlyForReadyPairsWithArtifactHref",
			"readyWhispersyncPairsWithoutArtifactHrefDoNotCreateLaunchCandidates",
			"pendingWhispersyncPairsDoNotCreateEbookLaunchCandidates",
			"decodesWhispersyncLastJobStateAndFractionalProgressFromNavicApiSchema"
		)
		assertContainsAll(
			sidecarModels + sidecarParserTest,
			"sidecar cue coverage",
			"audioResourceId",
			"audioTrackIndex",
			"audioHref",
			"audioStart",
			"audioEnd",
			"ebookHref",
			"spineIndex",
			"ebookStart",
			"ebookEnd",
			"audioCoverage",
			"ebookCoverage",
			"productionBinderySidecarCuesParseIntoTimelineSegments"
		)
		assertContainsAll(
			binderyModels + binderyMappings + resourceJsonTest,
			"resource audio quality coverage",
			"resourceKey",
			"audio",
			"bitrate",
			"bitrateKbps",
			"sampleRate",
			"sampleRateKHz",
			"qualityLabel",
			"qualityScore",
			"sourceRelease",
			"resourceJsonDecodesCurrentBinderyAudioMetadataSchema"
		)
		assertContainsAll(
			binderyModels + bookSyncJsonTest,
			"JSON audiobook detail coverage",
			"whispersyncAvailable",
			"whispersyncReadyCount",
			"whispersyncStatus",
			"provenance",
			"providerKind",
			"providerTitle",
			"metadataConfidenceScore",
			"coverUrl",
			"coverSource",
			"durationMs",
			"sizeBytes"
		)
		assertContainsAll(
			binderyModels + progressJsonTest + progressSyncTest,
			"progress schema coverage",
			"resourceKey",
			"href",
			"resourceHref",
			"positionMs",
			"durationMs",
			"completed",
			"updatedAt",
			"progressJsonDecodesCurrentBinderyProgressSchema",
			"binderyProgressMatchesCurrentBinderyHrefWhenLegacyResourceHrefIsAbsent",
			"binderyProgressMatchesCurrentBinderyResourceKeyWhenHrefIsAbsent"
		)
	}

	@Test
	fun binderyWhispersyncRoutesUseClientFacingOpdsContract() {
		val repository = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt")
			.readText()
		val apiClient = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/data/remote/bindery/BinderyApiClient.kt")
			.readText()
		val urlPolicy = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/data/remote/bindery/BinderyUrlPolicy.kt")
			.readText()
		val repositoryTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTest.kt")
			.readText()
		val progressTest = root
			.resolve("composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryProgressCacheTest.kt")
			.readText()
		val devContractTest = root
			.resolve("composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt")
			.readText()

		assertContainsAll(
			repository + apiClient + urlPolicy + repositoryTest + progressTest,
			"required OPDS route coverage",
			"getBookSync",
			"getWhispersyncSidecar",
			"getAudiobookDetail",
			"getAudiobookManifest",
			"getReadingProgress",
			"putReadingProgress",
			"/opds/books/",
			"/sync",
			"/progress",
			"fetchBookSync",
			"fetchWhispersyncSidecarJson",
			"fetchReadingProgress",
			"putReadingProgress",
			"progressFetchBaseUrls",
			"progressPutBaseUrls",
			"whispersyncSidecarPaths"
		)
		assertTrue(
			devContractTest.contains("Resolve-ReaderDevExplicitBinderyResource") &&
				devContractTest.contains("Resolved explicit reader target to Bindery OPDS resource"),
			"Reader validation must canonicalize direct /api/v1/book/{id}/file links back to OPDS resources before testing Whispersync/progress identity."
		)
	}

	private fun assertContainsAll(text: String, label: String, vararg needles: String) {
		val missing = needles.filterNot(text::contains)
		assertTrue(
			missing.isEmpty(),
			"$label is missing expected Bindery schema coverage: ${missing.joinToString()}"
		)
	}
}
