package paige.navic.reader

import java.io.File
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains

class FoliatePdfAnxParityTest {

	private val root: File = sequence {
		var candidate = kotlin.io.path.Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("composeApp/src/androidMain/assets/reader/vendor/foliate-js/pdf.js").exists()
	}.toFile()

	private val anxPdfText: String by lazy {
		readerUpstreamReferenceText("anx-reader", "assets/foliate-js/src/pdf.js")
	}

	private val navicPdfText: String by lazy {
		root.resolve("composeApp/src/androidMain/assets/reader/vendor/foliate-js/pdf.js").readText()
	}

	@Test
	fun navicPdfRuntimeCitesTheAnxMakePdfContract() {
		assertContains(
			navicPdfText,
			"tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614",
			message = "The PDF runtime must cite the exact Anx makePDF contract it is required to preserve."
		)
	}

	@Test
	fun navicPdfRuntimePreservesAnxMakePdfBookContract() {
		for (symbol in listOf(
			"export const makePDF = async file =>",
			"const data = new Uint8Array(await file.arrayBuffer())",
			"pdf.getMetadata()",
			"pdf.getOutline()",
			"book.toc = outline?.map(makeTOCItem)",
			"book.sections = Array.from({ length: pdf.numPages }).map",
			"id: i",
			"load: async () =>",
			"size: 1000",
			"book.sections[0].pageSpread = 'right'",
			"book.isExternal = uri => /^\\w+:/i.test(uri)",
			"book.resolveHref = async href =>",
			"pdf.getDestination(parsed)",
			"pdf.getPageIndex(dest[0])",
			"return { index }",
			"book.splitTOCHref = async href =>",
			"return [index, null]",
			"book.getTOCFragment = doc => doc.documentElement",
			"book.getCover = async () => renderPage(await pdf.getPage(1), true)"
		)) {
			assertContains(
				anxPdfText,
				symbol,
				message = "Anx reference no longer exposes expected PDF makePDF contract symbol: $symbol"
			)
			assertContains(
				navicPdfText,
				symbol,
				message = "Navic PDF runtime must preserve Anx makePDF contract symbol: $symbol"
			)
		}
		assertContains(
			navicPdfText,
			"rendition: { layout: 'pre-paginated'",
			message = "PDF books must remain pre-paginated so the Komikku shell can treat each page as a fixed page."
		)
		assertContains(
			navicPdfText,
			"pdfjs.getDocument({ data, isEvalSupported: false }).promise",
			message = "Navic must disable PDF.js eval support when parsing untrusted PDFs (CVE-2024-4367)."
		)
	}
}
