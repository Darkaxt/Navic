package paige.navic.ui.screens.reader

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import paige.navic.reader.BinderyReaderPublicationResolver
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderResolvedPublicationResource
import paige.navic.reader.readerPublicationCacheRoot

/** Public, locally authored fixed EPUB3; original supported file-source resolver/appassets route.
 * Caller may close ONLY after exact original physical returns. No account/network/bridge injection.
 */
internal class Task407PublicEpub private constructor(val resource: ReaderResolvedPublicationResource,
    private val source: File) : AutoCloseable {
    private var closeAttempted = false
    override fun close() {
        task407Require(!closeAttempted, "C407.Fixture.RepeatedMaterialReleaseNotAllowed")
        closeAttempted = true // Attempt only; not a release acknowledgement or retry permission.
        val failures = Task407FailureAccount()
        failures.attempt("C407.Fixture.OriginalSessionReleaseFailed") { resource.sessionLease.release() }
        failures.attempt("C407.Fixture.PublicSourceRemovalFailed") {
            task407Require(source.delete() || !source.exists(), "C407.Fixture.PublicSourceRemovalFailed")
        }
        failures.throwIfAny()
    }
    companion object {
        fun create(context: Context): Task407PublicEpub {
            task407Require(context.packageName == Task407RuntimePackage, "A407.Fixture.DisposableStorageRequired")
            val source = File.createTempFile("task407-public-", ".epub", context.cacheDir)
            var resolved: ReaderResolvedPublicationResource? = null
            try {
                source.writeBytes(bytes())
                val resource = runBlocking {
                    BinderyReaderPublicationResolver(
                        fetchResourceBytes = { throw Task407FixedFailure("E407.Fixture.UnexpectedResourceFetch") },
                        cacheRoot = readerPublicationCacheRoot(context)
                    ).resolve(ReaderPublicationResourceRequest(
                        bookId = "task407-public-runtime", title = "Public reader runtime fixture",
                        resourceHref = "task407-public-runtime.epub", sourceUrl = source.toURI().toString(),
                        kind = ReaderPublicationKind.Ebook, mediaOverlayEnabled = false
                    ))
                }
                resolved = resource
                return Task407PublicEpub(resource, source)
            } catch (failure: Throwable) {
                val failures = Task407FailureAccount()
                failures.record(failure, "E407.Fixture.PublicMaterialCreationFailed")
                failures.attempt("C407.Fixture.CreationSessionReleaseFailed") { resolved?.sessionLease?.release() }
                failures.attempt("C407.Fixture.CreationSourceRemovalFailed") {
                    task407Require(source.delete() || !source.exists(), "C407.Fixture.CreationSourceRemovalFailed")
                }
                failures.throwIfAny()
                throw Task407FixedFailure("E407.Fixture.PublicMaterialCreationFailed")
            }
        }
        private fun bytes(): ByteArray {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                fun entry(name: String, text: String, stored: Boolean = false) {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val entry = ZipEntry(name).apply {
                        time = 946684800000L
                        if (stored) {
                            method = ZipEntry.STORED
                            size = bytes.size.toLong()
                            compressedSize = size
                            crc = CRC32().apply { update(bytes) }.value
                        }
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
                entry("mimetype", "application/epub+zip", stored = true)
                entry("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""".trimIndent())
                entry("EPUB/package.opf", """<?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="fixture-id">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="fixture-id">urn:navic:public:task407-runtime</dc:identifier>
                    <dc:title>Public reader runtime fixture</dc:title><dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-10-14T00:00:00Z</meta>
                    </metadata><manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine page-progression-direction="ltr"><itemref idref="chapter"/></spine></package>""".trimIndent())
                entry("EPUB/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
                    <head><title>Contents</title></head><body><nav epub:type="toc"><h1>Contents</h1>
                    <ol><li><a href="chapter.xhtml">The public garden</a></li></ol></nav></body></html>""".trimIndent())
                val paragraphs = buildString {
                    repeat(48) { index ->
                        append("<p id=\"paragraph-")
                        append(index)
                        append("\">The public garden is open to every visitor. A winding path crosses the grass and reaches a small wooden bridge. Beyond the bridge, a quiet pond reflects the morning sky. We walk slowly, notice the changing light, and pause beside the water. This original passage is freely reusable for reader testing. Each paragraph remains ordinary reflowable text, with no forced page boundary or scripted navigation.</p>")
                    }
                }
                entry("EPUB/chapter.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml" lang="en"><head><title>The public garden</title></head>
                    <body><h1>The public garden</h1>$paragraphs</body></html>""".trimIndent())
            }
            return output.toByteArray()
        }
    }
}
