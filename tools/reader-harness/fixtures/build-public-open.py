"""Reproduce the checked-in, locally authored public-open.epub; no private input."""
from pathlib import Path
import zipfile

entries = {
    'mimetype': 'application/epub+zip',
    'META-INF/container.xml': '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>',
    'EPUB/package.opf': '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">public-open-test</dc:identifier><dc:title>Public open test</dc:title><dc:language>en</dc:language><meta property="dcterms:modified">2026-11-05T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>',
    'EPUB/nav.xhtml': '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">Public garden</a></li></ol></nav></body></html>',
    'EPUB/chapter.xhtml': '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Public garden</title></head><body><h1>Public garden</h1><p>The public garden is open to every visitor.</p></body></html>',
}
with zipfile.ZipFile(Path(__file__).with_name('public-open.epub'), 'w') as archive:
    for name, content in entries.items():
        entry = zipfile.ZipInfo(name, date_time=(2000, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_STORED
        entry.external_attr = 0o644 << 16
        archive.writestr(entry, content.encode('utf-8'))
