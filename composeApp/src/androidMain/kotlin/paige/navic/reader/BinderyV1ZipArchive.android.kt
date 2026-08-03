package paige.navic.reader

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

internal class BinderyV1BoundedZipArchive(
	publicationFile: java.io.File,
	private val limits: BinderyV1EpubVerificationLimits
) : Closeable {
	private val archive = RandomAccessFile(publicationFile, "r")
	private val centralDirectoryOffset: Long
	private val firstEntryByExactName: Map<String, BinderyV1ZipEntry>
	private var totalReadableBytes = 0L

	init {
		try {
			val directory = readCentralDirectory()
			centralDirectoryOffset = directory.offset
			firstEntryByExactName = directory.entries
		} catch (error: Throwable) {
			archive.close()
			throw error
		}
	}

	fun readFirstExact(name: String, maxBytes: Int): ByteArray? {
		val entry = firstEntryByExactName[name] ?: return null
		if (
			entry.uncompressedSize > maxBytes.toLong() ||
			entry.uncompressedSize > limits.maxTotalReadableEntryBytes.toLong() - totalReadableBytes
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
		}
		val localHeader = archive.readAt(entry.localHeaderOffset, BinderyZipLocalHeaderSize)
		if (
			localHeader.u32(0) != BinderyZipLocalHeaderSignature ||
			localHeader.u16(6) and BinderyZipEncryptedFlag != 0 ||
			localHeader.u16(8) != entry.compressionMethod
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		val dataOffset = entry.localHeaderOffset + BinderyZipLocalHeaderSize +
			localHeader.u16(26) + localHeader.u16(28)
		val dataEnd = dataOffset + entry.compressedSize
		if (
			dataOffset < entry.localHeaderOffset ||
			dataEnd < dataOffset ||
			dataEnd > centralDirectoryOffset
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		val compressed = BinderyRandomAccessSliceInputStream(
			archive = archive,
			start = dataOffset,
			length = entry.compressedSize
		)
		val input = when (entry.compressionMethod) {
			BinderyZipStored -> {
				if (entry.compressedSize != entry.uncompressedSize) {
					wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
				}
				compressed
			}
			BinderyZipDeflated -> InflaterInputStream(compressed, Inflater(true), BinderyZipBufferSize)
			else -> wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		val bytes = input.use { stream ->
			stream.readBounded(entry.uncompressedSize.toInt())
		}
		if (bytes.size.toLong() != entry.uncompressedSize) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		val crc = CRC32().apply { update(bytes) }.value
		if (crc != entry.crc32) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		totalReadableBytes += bytes.size.toLong()
		return bytes
	}

	override fun close() {
		archive.close()
	}

	private fun readCentralDirectory(): BinderyV1CentralDirectory {
		val fileLength = archive.length()
		val tailLength = minOf(fileLength, BinderyZipMaximumEocdSearch.toLong()).toInt()
		if (tailLength < BinderyZipEocdSize) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		val tailOffset = fileLength - tailLength
		val tail = archive.readAt(tailOffset, tailLength)
		val eocdIndex = (tail.size - BinderyZipEocdSize downTo 0).firstOrNull { index ->
			tail.u32(index) == BinderyZipEocdSignature &&
				index + BinderyZipEocdSize + tail.u16(index + 20) == tail.size
		} ?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		if (
			tail.u16(eocdIndex + 4) != 0 ||
			tail.u16(eocdIndex + 6) != 0 ||
			tail.u16(eocdIndex + 8) != tail.u16(eocdIndex + 10)
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		val entryCount = tail.u16(eocdIndex + 10)
		if (entryCount > limits.maxArchiveEntryCount) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
		}
		val directorySize = tail.u32(eocdIndex + 12)
		val directoryOffset = tail.u32(eocdIndex + 16)
		val eocdOffset = tailOffset + eocdIndex
		if (
			directoryOffset + directorySize < directoryOffset ||
			directoryOffset + directorySize > eocdOffset
		) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		var cursor = directoryOffset
		var totalNameBytes = 0L
		val entries = linkedMapOf<String, BinderyV1ZipEntry>()
		repeat(entryCount) {
			val header = archive.readAt(cursor, BinderyZipCentralHeaderSize)
			if (header.u32(0) != BinderyZipCentralHeaderSignature) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
			}
			val flags = header.u16(8)
			val compressionMethod = header.u16(10)
			val compressedSize = header.u32(20)
			val uncompressedSize = header.u32(24)
			val nameLength = header.u16(28)
			val extraLength = header.u16(30)
			val commentLength = header.u16(32)
			val diskStart = header.u16(34)
			val localHeaderOffset = header.u32(42)
			if (
				flags and BinderyZipEncryptedFlag != 0 ||
				diskStart != 0 ||
				compressedSize == BinderyZipU32Maximum ||
				uncompressedSize == BinderyZipU32Maximum ||
				localHeaderOffset == BinderyZipU32Maximum
			) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
			}
			totalNameBytes += nameLength.toLong()
			if (totalNameBytes > limits.maxArchiveEntryNameBytes.toLong()) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
			}
			val nameOffset = cursor + BinderyZipCentralHeaderSize
			val next = nameOffset + nameLength + extraLength + commentLength
			if (next < nameOffset || next > directoryOffset + directorySize) {
				wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
			}
			val name = archive.readAt(nameOffset, nameLength).binderyStrictUtf8()
			entries.putIfAbsent(
				name,
				BinderyV1ZipEntry(
					compressionMethod = compressionMethod,
					crc32 = header.u32(16),
					compressedSize = compressedSize,
					uncompressedSize = uncompressedSize,
					localHeaderOffset = localHeaderOffset
				)
			)
			cursor = next
		}
		if (cursor > directoryOffset + directorySize) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		return BinderyV1CentralDirectory(offset = directoryOffset, entries = entries)
	}
}

private data class BinderyV1CentralDirectory(
	val offset: Long,
	val entries: Map<String, BinderyV1ZipEntry>
)

private data class BinderyV1ZipEntry(
	val compressionMethod: Int,
	val crc32: Long,
	val compressedSize: Long,
	val uncompressedSize: Long,
	val localHeaderOffset: Long
)

private class BinderyRandomAccessSliceInputStream(
	private val archive: RandomAccessFile,
	start: Long,
	length: Long
) : InputStream() {
	private var remaining = length

	init {
		archive.seek(start)
	}

	override fun read(): Int {
		if (remaining <= 0L) return -1
		val value = archive.read()
		if (value < 0) return -1
		remaining -= 1
		return value
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		if (remaining <= 0L) return -1
		val requested = minOf(length.toLong(), remaining).toInt()
		val read = archive.read(buffer, offset, requested)
		if (read < 0) return -1
		remaining -= read.toLong()
		return read
	}

	override fun close() = Unit
}

private fun InputStream.readBounded(expectedSize: Int): ByteArray {
	val output = ByteArrayOutputStream(minOf(expectedSize, BinderyZipBufferSize))
	val buffer = ByteArray(BinderyZipBufferSize)
	while (true) {
		val read = read(buffer)
		if (read < 0) break
		if (read == 0) continue
		if (output.size().toLong() + read > expectedSize.toLong()) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		output.write(buffer, 0, read)
	}
	return output.toByteArray()
}

private fun RandomAccessFile.readAt(offset: Long, length: Int): ByteArray {
	if (offset < 0L || length < 0 || offset + length < offset || offset + length > this.length()) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
	}
	val bytes = ByteArray(length)
	seek(offset)
	readFully(bytes)
	return bytes
}

private fun ByteArray.u16(offset: Int): Int =
	(this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.u32(offset: Int): Long =
	u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)

private const val BinderyZipStored = 0
private const val BinderyZipDeflated = 8
private const val BinderyZipEncryptedFlag = 1
private const val BinderyZipBufferSize = 8 * 1024
private const val BinderyZipLocalHeaderSize = 30
private const val BinderyZipCentralHeaderSize = 46
private const val BinderyZipEocdSize = 22
private const val BinderyZipMaximumEocdSearch = 65_535 + BinderyZipEocdSize
private const val BinderyZipU32Maximum = 0xffffffffL
private const val BinderyZipLocalHeaderSignature = 0x04034b50L
private const val BinderyZipCentralHeaderSignature = 0x02014b50L
private const val BinderyZipEocdSignature = 0x06054b50L
