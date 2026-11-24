package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Chapter illustration plates (Darkaxt/Navic#74): source-shape pins for the reader runtime.
class ReaderIllustrationPlateSourceTest {
	private val root = readerAssetRoot()
	private val renderProfile = root.resolve("navic-reader-render-profile.js").readText()
	private val settingsCore = root.resolve("navic-reader-settings-core.js").readText()
	private val wordSync = root.resolve("navic-reader-wordsync-provenance.js").readText()

	private val plate = renderProfile
		.substringAfter("export const applyReaderIllustrationPlate")
		.substringBefore("const ReaderIllustrationEdgeMask")

	@Test
	fun plateIsInsertedFirstInBodyOnlyForTheExactSectionAtByteZero() {
		val match = renderProfile
			.substringAfter("export const readerIllustrationPlacementForSection")
			.substringBefore("const readerIllustrationPlateHeight")
		assertContains(match, "placement.spineIndex === identity.spineIndex")
		assertContains(match, "placement.ebookStart === 0")
		assertContains(match, "normalizedIllustrationHref(placement.ebookHref) === identity.href")
		val identity = renderProfile
			.substringAfter("export const readerIllustrationSectionIdentity")
			.substringBefore("const positiveInteger")
		// Foliate's compact section index is mapped to the raw OPF spine index, as WordSync does.
		assertContains(identity, "resources.getItemByID(resources.spine[spineIndex]?.idref)")
		assertContains(identity, "if (!section || section.id !== item.href) return null")
		assertContains(plate, "body.insertBefore(figure, body.firstChild)")
		assertContains(plate, "readerIllustrationSectionIdentity(book, sectionIndex)")
	}

	@Test
	fun plateIsIdempotentReplacedOnChangeAndRemovedWhenDisabled() {
		assertContains(plate, "existing[0] === body.firstChild")
		assertContains(plate, "readerIllustrationPlateMatches(existing[0], placement, height)")
		assertTrue(
			plate.indexOf("for (const element of existing) element.remove()") <
				plate.indexOf("if (!placement) return null"),
			"Stale or disabled plates are removed before the no-placement exit."
		)
		assertContains(settingsCore, "settings?.illustrationsEnabled !== false")
	}

	@Test
	fun plateCarriesNoTextNoAltAndNoCaption() {
		assertContains(plate, "image.setAttribute('alt', '')")
		assertContains(plate, "image.setAttribute('decoding', 'sync')")
		assertContains(plate, "figure.append(image)")
		assertFalse(plate.contains("createTextNode"))
		assertFalse(plate.contains("figcaption"))
		assertFalse(plate.contains("textContent"))
		assertFalse(plate.contains("innerHTML"))
	}

	@Test
	fun plateFillsOnePageContainedWithSoftenedEdges() {
		val css = renderProfile
			.substringAfter("export const readerIllustrationCss")
			.substringBefore("export const applyReaderContentDocumentRenderProfile")
		assertContains(css, "break-after: column !important;")
		assertContains(css, "object-fit: contain !important;")
		assertContains(css, "max-width: 100% !important;")
		assertContains(css, "max-height: 100% !important;")
		// The paginator would cap every image at the page minus twice its margin (~60% of the page).
		val paginator = root.resolve("vendor/foliate-js/paginator.js").readText()
			.substringAfter("setImageSize() {")
			.substringBefore("expand() {")
		assertContains(paginator, "if (el.hasAttribute('data-navic-illustration-image')) continue")
		assertContains(css, "-webkit-mask-image: \${ReaderIllustrationEdgeMask};")
		assertContains(css, "mask-image: \${ReaderIllustrationEdgeMask};")
		assertContains(css, "-webkit-mask-composite: source-in;")
		assertContains(css, "mask-composite: intersect;")
		assertContains(renderProfile, "linear-gradient(to right, transparent, #000 7%, #000 93%, transparent)")
		assertContains(renderProfile, "linear-gradient(to bottom, transparent, #000 7%, #000 93%, transparent)")
		assertContains(renderProfile, "if (flow !== ReaderFlowScrolled && flow !== ReaderFlowScrolledGaps) return '100vh'")
		assertContains(renderProfile, "themeStyle.textContent = `\${readerContentCss(settings)}\\n\${readerIllustrationCss()}`")
	}

	@Test
	fun chapterOpeningDetectionLooksPastThePlate() {
		val typography = root.resolve("navic-reader-typography.js").readText()
		val firstVisible = typography
			.substringAfter("const readerFirstVisibleContentElement = doc =>")
			.substringBefore("const readerChapterOpeningHeading = doc =>")
		assertContains(firstVisible, ".filter(element => !element.closest(readerIllustrationPlateSelector))")
		assertContains(typography, "const readerIllustrationPlateSelector = '[data-navic-illustration]'")
	}

	@Test
	fun wordSyncIgnoresThePlateSoByteOffsetsAreUnchanged() {
		val mutation = wordSync
			.substringAfter("const mutationCanChangeDocumentTokens = mutation => {")
			.substringBefore("const SelectedClosingElementNames")
		assertContains(mutation, "if (nodeIsInIllustrationPlate(mutation?.target)) return false")
		assertContains(mutation, "!nodeIsInIllustrationPlate(node)")
		val projection = wordSync
			.substringAfter("const rawDomProjectionAtoms = doc => {")
			.substringBefore("const asciiWhitespaceAtom")
		// The plate contributes no projection atoms, not even the synthetic tag spaces.
		assertTrue(
			projection.indexOf("if (node.hasAttribute?.(ReaderIllustrationPlateAttribute)) return") <
				projection.indexOf("appendSyntheticProjectionText(atoms, ' ', projectionPoint(node, 0))")
		)
		assertContains(wordSync, "const ReaderIllustrationPlateAttribute = 'data-navic-illustration'")
		// Extraction reads the raw publication source, never the decorated DOM.
		val extraction = wordSync
			.substringAfter("export const extractBinderyV1Text = source => {")
			.substringBefore("const tokenizeText")
		assertFalse(extraction.contains("doc"))
	}

	@Test
	fun illustrationsDigestKeysPaginationAndBothDecorationFingerprints() {
		assertContains(settingsCore, "export const readerIllustrationsDigest = settings =>")
		val pagination = root.resolve("navic-reader-pagination.js").readText()
			.substringAfter("function readerPaginationSettingsMetadata(settings = {}) {")
			.substringBefore("\nfunction readerSettingsRequirePaginationReplacement")
		assertContains(pagination, "illustrationsDigest: readerIllustrationsDigest(settings),")
		// The cache key hashes a whitelist of the metadata; plates on and off must not share it.
		val renderState = root.resolve("navic-reader-pagination-model.js").readText()
			.substringAfter("export const readerPaginationRenderState = input => ({")
			.substringBefore("export const readerPaginationFingerprint")
		assertContains(renderState, "{ illustrationsDigest: String(input.illustrationsDigest) }")
		val planned = renderProfile
			.substringAfter("export const readerLiveIssuedRasterPlan")
			.substringBefore("const rasterProfileKey")
		assertContains(planned, "illustrationsDigest: readerIllustrationsDigest(settings),")
		val preview = root.resolve("navic-reader-page-turn-preview.js").readText()
			.substringAfter("function pageTurnRasterDescriptor(")
			.substringBefore("function pageTurnRasterPreparationPlan(")
		assertContains(preview, "illustrationsDigest: readerIllustrationsDigest(settings),")
		assertContains(preview, "contentRenderRevision: 4,")
		assertContains(renderProfile, "export const ReaderContentRenderRevision = 4")
	}

	@Test
	fun realizedRasterWaitsForSettledPlates() {
		val observation = renderProfile
			.substringAfter("export const readerIllustrationAssetObservation")
			.substringBefore("export const readerRealizedRasterObservation")
		assertContains(observation, "if (!image.complete) return null")
		assertContains(observation, "loaded: Number(image.naturalWidth) > 0")
		val realized = renderProfile.substringAfter("export const readerRealizedRasterObservation")
		assertContains(realized, "if (!illustrationAssets) return null")
		assertContains(realized, "loadedAssetUrls,\n    illustrationAssets,\n  }))")
	}

	@Test
	fun everyContentThemeCallerPassesTheSectionIndex() {
		val appearance = root.resolve("navic-reader-appearance.js").readText()
		assertContains(appearance, "function applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
		assertContains(appearance, "applyReaderContentDocumentRenderProfile(doc, settings, index, this?.view?.book)")
		assertContains(appearance, "this.applyDocumentTheme(content.doc, settings, content.index)")
		assertContains(root.resolve("navic-reader.js").readText(), "this.applyDocumentTheme(doc, this.readerSettings, index)")
		assertContains(root.resolve("navic-reader-pagination.js").readText(), "this.applyDocumentTheme(detail.doc, settings, detail.index)")
		val preview = root.resolve("navic-reader-page-turn-preview.js").readText()
		assertContains(preview, "this.applyDocumentTheme(detail.doc, this.readerSettings, detail.index)")
		assertContains(preview, "this.applyDocumentTheme(content.doc, settings, content.index)")
		assertContains(root.resolve("navic-reader-page-turns.js").readText(), "this.applyDocumentTheme(doc, this.readerSettings, targetIndex)")
		val production = root.resolve("passive-raster-prototype/production-raster-foliate-session.js").readText()
		assertContains(production, "applyReaderContentDocumentRenderProfile(content.doc, settings, content.index, view.book)")
		assertContains(production, "applyReaderContentDocumentRenderProfile(doc, settings, event.detail?.index, view.book)")
		val parity = root.resolve("passive-raster-prototype/render-parity-fixture.js").readText()
		assertContains(parity, "NavicReaderAppearanceMethods.applyDocumentTheme.call({ view }, doc, settings, event.detail?.index)")
		root.walkTopDown()
			.filter { it.isFile && it.extension == "js" && !it.path.replace('\\', '/').contains("/vendor/") }
			.forEach { file ->
				assertFalse(
					Regex("applyReaderContentDocumentRenderProfile\\([^,()]+, [^,()]+\\)").containsMatchIn(file.readText()),
					"${file.name} must pass the section index to the content render profile."
				)
			}
	}
}
