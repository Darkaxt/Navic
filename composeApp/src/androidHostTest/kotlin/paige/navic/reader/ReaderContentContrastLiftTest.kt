package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderContentContrastLiftTest {
	private val renderProfile = readerAssetRoot().resolve("navic-reader-render-profile.js").readText()
	private val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

	@Test
	fun unreadableBookTextTakesTheThemeForegroundAfterBackgroundsAreStripped() {
		val profile = renderProfile
			.substringAfter("export const applyReaderContentDocumentRenderProfile")
			.substringBefore("\n}\n")
		val stripped = profile.indexOf("'background-image': 'none',\n    })")
		val lifted = profile.indexOf("liftUnreadableTextColours(doc, palette)")
		assertTrue(stripped in 0 until lifted, "contrast lift must run after book backgrounds are stripped")

		val lift = renderProfile
			.substringAfter("const liftUnreadableTextColours")
			.substringBefore("const normalizedDirection")
		// Re-applies (theme switches) first restore the book's own inline colour.
		assertTrue(lift.indexOf("removeAttribute(ReaderContrastLiftedAttribute)") < lift.indexOf("getComputedStyle"))
		assertContains(lift, "readerTextContrastRatio(colour, background) < ReaderMinimumTextContrast")
		assertContains(lift, "isThemeBackgroundMediaElement(element) || element.closest('svg, math')")
		assertContains(lift, "element.style.setProperty('color', 'var(--reader-foreground)', 'important')")
		assertContains(renderProfile, "const ReaderMinimumTextContrast = 2")
	}

	@Test
	fun cachedRastersRetireWhenTheContentRenderRevisionChanges() {
		val revision = Regex("export const ReaderContentRenderRevision = (\\d+)")
			.find(renderProfile)?.groupValues?.get(1)
		assertTrue(revision != null, "render profile must declare the content render revision")
		// Revision 3: chapter illustration plates (Darkaxt/Navic#74) change content pixels.
		// Revision 4: plate pages are no longer stored as monochrome paper/text splits (#58).
		// Revision 5: mid-chapter plates (plate-before-block) and their spread spacers.
		// Revision 6: NBSP-padded chapter numerals are centred.
		assertTrue(revision == "6", "centred chapter numerals must retire revision-5 rasters")
		assertContains(renderProfile, "contentRenderRevision: ReaderContentRenderRevision,")
		val descriptor = preview
			.substringAfter("function pageTurnRasterDescriptor(")
			.substringBefore("function pageTurnRasterPreparationPlan(")
		// The planned and realized raster keys must hash the same decoration state.
		assertContains(descriptor, "contentRenderRevision: $revision,")
	}

	@Test
	fun nbspPaddedChapterNumeralsAreCentredWithoutEditingTheirText() {
		val typography = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val centre = typography
			.substringAfter("export const readerCentreSpacePaddedNumerals = doc => {")
			.substringBefore("export const readerFontWeightValue")
		// Only padding plus one short numeral, padding in its own elements; text nodes are never touched.
		assertContains(centre, "!ReaderPaddedNumeralPattern.test(text)) continue")
		assertContains(centre, "if (!padding.length || strayPadding) continue")
		assertContains(centre, "child.style.setProperty('display', 'none', 'important')")
		assertContains(centre, "block.style.setProperty('text-align', 'center', 'important')")
		assertFalse(centre.contains("nodeValue ="))
		assertFalse(centre.contains("textContent ="))
		assertContains(renderProfile, "readerFitOverlongHeadingWords(doc)\n  readerCentreSpacePaddedNumerals(doc)")
	}

	@Test
	fun overlongHeadingWordsShrinkToTheColumnInsteadOfSplitting() {
		val typography = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val fit = typography
			.substringAfter("const readerFitHeadingWords = heading => {")
			.substringBefore("export const readerFitOverlongHeadingWords")
		// Every fit starts from the heading's own size, then measures without the split.
		assertTrue(fit.indexOf("heading.removeAttribute(ReaderHeadingFitAttribute)") < fit.indexOf("heading.scrollWidth"))
		assertTrue(fit.indexOf("style.setProperty('overflow-wrap', 'normal', 'important')") < fit.indexOf("heading.scrollWidth"))
		assertContains(fit, "Math.max(ReaderHeadingFitMinimumScale, (columnWidth / contentWidth) * 0.98)")
		val install = typography
			.substringAfter("export const readerFitOverlongHeadingWords")
			.substringBefore("export const readerNormalizeChapterOpeningMargins")
		assertContains(install, "doc.defaultView?.ResizeObserver")
		assertContains(install, "if (widths.get(entry.target) === width) continue")
		assertContains(renderProfile, "readerNormalizeChapterOpeningMargins(doc, settings)\n  readerFitOverlongHeadingWords(doc)")
	}

	@Test
	fun contrastMathMatchesWcag() {
		assertContains(renderProfile, "0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)")
		assertContains(renderProfile, "(Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)")
	}
}
