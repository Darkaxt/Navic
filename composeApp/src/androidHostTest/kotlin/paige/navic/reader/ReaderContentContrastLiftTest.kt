package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
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
		assertContains(renderProfile, "contentRenderRevision: ReaderContentRenderRevision,")
		val descriptor = preview
			.substringAfter("function pageTurnRasterDescriptor(")
			.substringBefore("function pageTurnRasterPreparationPlan(")
		// The planned and realized raster keys must hash the same decoration state.
		assertContains(descriptor, "contentRenderRevision: $revision,")
	}

	@Test
	fun contrastMathMatchesWcag() {
		assertContains(renderProfile, "0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)")
		assertContains(renderProfile, "(Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)")
	}
}
