package paige.navic.shared

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.domain.manager.PlaybackLedgerExport
import paige.navic.util.core.Logger
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Serializable
internal data class PlaybackLedgerSilence(val atMs: Long, val lengthMs: Long)

/** One played song. Durations are milliseconds; [startedAt] is wall-clock epoch milliseconds. */
@Serializable
internal data class PlaybackLedgerEntry(
	val startedAt: Long,
	val songId: String,
	val title: String? = null,
	val artist: String? = null,
	val source: String? = null,
	val serverDurationMs: Long? = null,
	val playerDurationMs: Long? = null,
	val playedMs: Long = 0,
	val endPositionMs: Long? = null,
	val endReason: String = "",
	val stalls: Int = 0,
	val stallMs: Long = 0,
	val silences: List<PlaybackLedgerSilence> = emptyList(),
	val errors: List<String> = emptyList(),
	val flags: List<String> = emptyList()
)

internal const val LedgerEndToleranceMs = 5_000L

/** Anomalies worth a look: a "finished" song that stopped well short of the server's length, etc. */
internal fun PlaybackLedgerEntry.ledgerFlags(): List<String> = buildList {
	val server = serverDurationMs?.takeIf { it > 0 }
	if (endReason == "finished" && server != null && endPositionMs != null &&
		endPositionMs < server - LedgerEndToleranceMs) add("ended-early")
	if (server != null && playerDurationMs != null && playerDurationMs > 0 &&
		playerDurationMs < server - LedgerEndToleranceMs) add("short-stream")
	if (stalls > 0) add("stalled")
	if (silences.isNotEmpty()) add("silence")
	if (errors.isNotEmpty()) add("error")
}

/** Player-independent bookkeeping for the song that is currently playing. */
internal class PlaybackLedgerRecorder(
	private val elapsed: () -> Long,
	private val wall: () -> Long
) {
	private var entry: PlaybackLedgerEntry? = null
	private var playingSince: Long? = null
	private var stallSince: Long? = null
	private var readyOnce = false
	private var anchorPositionMs = 0L
	private var anchorAt = 0L
	private var anchorPlaying = false

	val currentSongId: String? get() = entry?.songId

	fun begin(songId: String, title: String?, artist: String?, serverDurationMs: Long?, source: String?, isPlaying: Boolean, positionMs: Long) {
		entry = PlaybackLedgerEntry(
			startedAt = wall(), songId = songId, title = title, artist = artist,
			source = source, serverDurationMs = serverDurationMs?.takeIf { it > 0 }
		)
		playingSince = if (isPlaying) elapsed() else null
		stallSince = null
		readyOnce = isPlaying
		anchor(positionMs, isPlaying)
	}

	fun anchor(positionMs: Long, isPlaying: Boolean) {
		anchorPositionMs = positionMs.coerceAtLeast(0)
		anchorAt = elapsed()
		anchorPlaying = isPlaying
	}

	fun onIsPlaying(isPlaying: Boolean, positionMs: Long) {
		val now = elapsed()
		if (isPlaying) {
			if (playingSince == null) playingSince = now
		} else {
			closePlaying(now)
		}
		anchor(positionMs, isPlaying)
	}

	fun onPlaybackState(buffering: Boolean, ready: Boolean, playWhenReady: Boolean) {
		val now = elapsed()
		if (ready) readyOnce = true
		if (buffering && playWhenReady && readyOnce && stallSince == null && entry != null) {
			stallSince = now
			update { copy(stalls = stalls + 1) }
		}
		if (!buffering) closeStall(now)
	}

	fun onPlayerDuration(durationMs: Long) {
		if (durationMs > 0) update { copy(playerDurationMs = durationMs) }
	}

	fun onError(code: String) = update { copy(errors = (errors + code).takeLast(MaxErrors)) }

	fun onSilence(lengthMs: Long, atMs: Long) = update {
		copy(silences = (silences + PlaybackLedgerSilence(atMs.coerceAtLeast(0), lengthMs)).takeLast(MaxSilences))
	}

	/** Closes the current song; [endPositionMs] null means "estimate from the last known position". */
	fun end(reason: String, endPositionMs: Long?, streamDetail: String? = null): PlaybackLedgerEntry? {
		val current = entry ?: return null
		val now = elapsed()
		val position = endPositionMs?.takeIf { it >= 0 } ?: estimatedPositionMs(now)
		closePlaying(now)
		closeStall(now)
		val closed = (entry ?: current).let { closing ->
			closing.copy(
				endPositionMs = position,
				endReason = reason,
				source = if (closing.source == "stream" && streamDetail != null) "stream:$streamDetail" else closing.source
			)
		}
		entry = null
		return closed.copy(flags = closed.ledgerFlags())
	}

	private fun estimatedPositionMs(now: Long): Long =
		anchorPositionMs + if (anchorPlaying) (now - anchorAt).coerceAtLeast(0) else 0

	private fun closePlaying(now: Long) {
		playingSince?.let { since -> update { copy(playedMs = playedMs + (now - since).coerceAtLeast(0)) } }
		playingSince = null
	}

	private fun closeStall(now: Long) {
		stallSince?.let { since -> update { copy(stallMs = stallMs + (now - since).coerceAtLeast(0)) } }
		stallSince = null
	}

	private inline fun update(change: PlaybackLedgerEntry.() -> PlaybackLedgerEntry) {
		entry = entry?.change()
	}

	private companion object {
		const val MaxErrors = 5
		const val MaxSilences = 20
	}
}

internal fun ledgerEndReason(discontinuityReason: Int): String = when (discontinuityReason) {
	Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "finished"
	Player.DISCONTINUITY_REASON_SEEK -> "skipped"
	Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "seek-adjusted"
	Player.DISCONTINUITY_REASON_SKIP -> "skipped-unplayable"
	Player.DISCONTINUITY_REASON_REMOVE -> "removed"
	else -> "internal"
}

/**
 * Service-side [Player.Listener] feeding [PlaybackLedgerRecorder] and persisting each finished
 * song. Must be called on the player's application thread, except [onStreamOpen].
 */
internal class AndroidPlaybackLedger(
	private val player: Player,
	private val store: AndroidPlaybackLedgerStore,
	private val recorder: PlaybackLedgerRecorder = PlaybackLedgerRecorder(SystemClock::elapsedRealtime, System::currentTimeMillis)
) : Player.Listener {
	private val streamDetails = ConcurrentHashMap<String, String>()
	private var pendingEnd: Pair<String, Long>? = null

	fun start() {
		player.addListener(this)
		player.currentMediaItem?.let(::begin)
	}

	fun close() {
		finish("stopped", player.currentPosition)
		player.removeListener(this)
	}

	/** Which stream variant was opened for a song; called from loader threads. */
	fun onStreamOpen(songId: String, quality: String, fullyCached: Boolean) {
		streamDetails[songId] = if (fullyCached) "$quality,cached" else quality
	}

	fun onSilence(lengthMs: Long) = recorder.onSilence(lengthMs, player.currentPosition)

	override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
		val sameItem = oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
			oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId
		if (sameItem && reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
			recorder.anchor(newPosition.positionMs, player.isPlaying)
		} else {
			pendingEnd = ledgerEndReason(reason) to oldPosition.positionMs
		}
	}

	override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
		val (endReason, position) = pendingEnd
			?: ((if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) "queue-changed" else "internal") to null)
		pendingEnd = null
		finish(endReason, position)
		mediaItem?.let(::begin)
	}

	override fun onIsPlayingChanged(isPlaying: Boolean) {
		if (isPlaying && recorder.currentSongId == null) player.currentMediaItem?.let(::begin)
		recorder.onPlayerDuration(player.duration)
		recorder.onIsPlaying(isPlaying, player.currentPosition)
	}

	override fun onPlaybackStateChanged(playbackState: Int) {
		recorder.onPlaybackState(
			buffering = playbackState == Player.STATE_BUFFERING,
			ready = playbackState == Player.STATE_READY,
			playWhenReady = player.playWhenReady
		)
		if (playbackState == Player.STATE_READY) recorder.onPlayerDuration(player.duration)
		if (playbackState == Player.STATE_ENDED) finish("queue-ended", player.duration.takeIf { it != C.TIME_UNSET })
	}

	override fun onPlayerError(error: PlaybackException) = recorder.onError(error.errorCodeName)

	private fun begin(item: MediaItem) {
		val metadata = item.mediaMetadata
		recorder.begin(
			songId = item.mediaId,
			title = metadata.title?.toString(),
			artist = metadata.artist?.toString(),
			serverDurationMs = metadata.durationMs,
			source = when (val scheme = item.localConfiguration?.uri?.scheme) {
				"http", "https" -> "stream"
				"file", "content" -> "local"
				null -> null
				else -> scheme
			},
			isPlaying = player.isPlaying,
			positionMs = player.currentPosition
		)
	}

	private fun finish(reason: String, positionMs: Long?) {
		val songId = recorder.currentSongId ?: return
		recorder.end(reason, positionMs, streamDetails[songId])?.let(store::append)
	}
}

/** Append-only JSON-lines ledger in app storage, trimmed to the most recent [KeepEntries] songs. */
internal class AndroidPlaybackLedgerStore(context: Context) : PlaybackLedgerExport {
	private val file = File(context.filesDir, "playback-ledger.jsonl")
	private val lock = Any()
	private val writer = Executors.newSingleThreadExecutor { runnable ->
		Thread(runnable, "navic-playback-ledger").apply { isDaemon = true }
	}
	private val json = Json { ignoreUnknownKeys = true }

	fun append(entry: PlaybackLedgerEntry) {
		val line = json.encodeToString(entry)
		writer.execute {
			synchronized(lock) {
				runCatching {
					file.appendText(line + "\n")
					if (file.length() > TrimBytes) {
						file.writeText(file.readLines().filter(String::isNotBlank).takeLast(KeepEntries).joinToString("\n", postfix = "\n"))
					}
				}.onFailure { Logger.w("PlaybackLedger", "Could not write the playback ledger", it) }
			}
		}
	}

	private fun readEntries(): List<PlaybackLedgerEntry> = synchronized(lock) {
		if (!file.isFile) return emptyList()
		file.readLines().mapNotNull { line ->
			line.takeIf(String::isNotBlank)?.let { runCatching { json.decodeFromString<PlaybackLedgerEntry>(it) }.getOrNull() }
		}
	}

	override suspend fun entryCount(): Int = withContext(Dispatchers.IO) { readEntries().size }

	override suspend fun exportText(): String = withContext(Dispatchers.IO) { formatPlaybackLedger(readEntries()) }

	override suspend fun clear() = withContext(Dispatchers.IO) {
		synchronized(lock) { file.delete() }
		Unit
	}

	private companion object {
		const val KeepEntries = 2_000
		const val TrimBytes = 1_500_000L
	}
}

internal fun formatPlaybackLedger(entries: List<PlaybackLedgerEntry>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
	val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
	append("Navic playback ledger: ${entries.size} songs, oldest first.\n")
	append("Flags: ended-early = finished more than 5 s before the server's length; ")
	append("short-stream = the player saw a shorter file than the server reports; ")
	append("silence = at least 2 s of near-digital silence (position is approximate).\n")
	for (entry in entries) {
		append('\n')
		append(time.format(Instant.ofEpochMilli(entry.startedAt)))
		append(" | ").append(entry.title ?: "?")
		entry.artist?.let { append(" - ").append(it) }
		append(" | played ").append(clock(entry.playedMs))
		append(" | stopped at ").append(entry.endPositionMs?.let(::clock) ?: "?")
		append(" of ").append(entry.serverDurationMs?.let(::clock) ?: "?")
		entry.playerDurationMs?.takeIf { it != entry.serverDurationMs }?.let { append(" (player ").append(clock(it)).append(')') }
		append(" | ").append(entry.endReason)
		entry.source?.let { append(" | ").append(it) }
		if (entry.stalls > 0) append(" | stalls ").append(entry.stalls).append(" (").append(seconds(entry.stallMs)).append(')')
		if (entry.silences.isNotEmpty()) {
			append(" | silence ")
			append(entry.silences.joinToString(", ") { "${seconds(it.lengthMs)} at ~${clock(it.atMs)}" })
		}
		if (entry.errors.isNotEmpty()) append(" | errors ").append(entry.errors.joinToString(", "))
		if (entry.flags.isNotEmpty()) append(" | FLAGS ").append(entry.flags.joinToString(", "))
		append(" | id ").append(entry.songId)
	}
	append('\n')
}

private fun clock(ms: Long): String {
	val totalSeconds = ms.coerceAtLeast(0) / 1_000
	return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

private fun seconds(ms: Long): String = "%.1fs".format(java.util.Locale.ROOT, ms / 1_000.0)
