package paige.navic.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.domain.models.shouldPersistAppLogEvent
import paige.navic.util.core.AppLogLevel
import paige.navic.util.core.Logger
import paige.navic.util.core.LoggerEvent
import kotlin.time.Clock

class AppLogManager(
	private val preferenceManager: PreferenceManager,
	private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
	private val maxEntries: Int = DefaultMaxEntries
) {
	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}
	private var store = restoreStore()
	private val _entries = MutableStateFlow(store.entries)
	private var started = false

	val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

	fun start() {
		if (started) return
		started = true
		Logger.configureIssueLogSink(::record)
	}

	fun setEnabled(value: Boolean) {
		preferenceManager.issueLoggingEnabled = value
		if (!value) {
			retainAlwaysPersistedEntries()
		}
	}

	fun record(event: LoggerEvent) {
		if (!shouldPersistAppLogEvent(preferenceManager.issueLoggingEnabled, event.tag)) return
		val entry = AppLogEntry(
			id = store.nextId,
			timestampMillis = clockMillis(),
			level = event.level,
			tag = event.tag.sanitizedLogField(MaxTagLength),
			message = event.message.sanitizedLogField(MaxMessageLength),
			throwable = event.throwable
				?.stackTraceToString()
				?.sanitizedLogField(MaxThrowableLength)
		)
		store = AppLogStore(
			nextId = store.nextId + 1,
			entries = (store.entries + entry).takeLast(maxEntries.coerceAtLeast(1))
		)
		persistStore()
	}

	fun clear() {
		store = AppLogStore()
		_entries.value = emptyList()
		preferenceManager.issueLogJson = ""
	}

	private fun retainAlwaysPersistedEntries() {
		val retained = store.entries.filter { entry ->
			shouldPersistAppLogEvent(issueLoggingEnabled = false, tag = entry.tag)
		}
		if (retained.isEmpty()) {
			clear()
			return
		}
		store = store.copy(entries = retained)
		persistStore()
	}

	fun exportText(): String =
		entries.value.joinToString(separator = "\n\n") { entry ->
			buildString {
				append(entry.timestampMillis)
				append(" ")
				append(entry.level.shortLabel)
				append("/")
				append(entry.tag)
				append(": ")
				append(entry.message)
				entry.throwable?.takeIf { it.isNotBlank() }?.let { throwable ->
					append("\n")
					append(throwable)
				}
			}
		}

	private fun restoreStore(): AppLogStore =
		preferenceManager.issueLogJson
			.takeIf { it.isNotBlank() }
			?.let { encoded ->
				runCatching { json.decodeFromString<AppLogStore>(encoded) }.getOrNull()
			}
			?.let { decoded ->
				val entries = decoded.entries.takeLast(maxEntries.coerceAtLeast(1))
				decoded.copy(
					nextId = decoded.nextId.coerceAtLeast((entries.maxOfOrNull { it.id } ?: 0L) + 1L),
					entries = entries
				)
			}
			?: AppLogStore()

	private fun persistStore() {
		preferenceManager.issueLogJson = json.encodeToString(store)
		_entries.value = store.entries
	}

	companion object {
		const val DefaultMaxEntries = 500
		private const val MaxTagLength = 80
		private const val MaxMessageLength = 2000
		private const val MaxThrowableLength = 8000
	}
}

@Serializable
data class AppLogEntry(
	val id: Long,
	val timestampMillis: Long,
	val level: AppLogLevel,
	val tag: String,
	val message: String,
	val throwable: String? = null
)

@Serializable
private data class AppLogStore(
	val nextId: Long = 1L,
	val entries: List<AppLogEntry> = emptyList()
)

private fun String.sanitizedLogField(maxLength: Int): String =
	take(maxLength).replace(Regex("[\\u0000-\\u001f&&[^\\n\\r\\t]]"), " ")
