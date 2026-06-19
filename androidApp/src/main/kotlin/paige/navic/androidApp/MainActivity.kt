package paige.navic.androidApp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.App
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.VolumeKeySkipAction
import paige.navic.domain.models.VolumeKeySkipEventAction
import paige.navic.domain.models.VolumeKeySkipKey
import paige.navic.domain.models.volumeKeySkipDecision
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderWebRuntime
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.lidaClips.LidaClipPictureInPictureCoordinator

class MainActivity : ComponentActivity(), KoinComponent {
	private val preferenceManager: PreferenceManager by inject()
	private val player: MediaPlayerViewModel by inject()
	private var readerDevInitialScreen by mutableStateOf<Screen?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		ReaderWebRuntime.setForceWebContentsDebuggingEnabled(BuildConfig.NAVIC_READER_DEV)
		applyReaderDevIntentSeed(intent)
		enableEdgeToEdge()
		setContent { App(initialScreenOverride = readerDevInitialScreen) }
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		applyReaderDevIntentSeed(intent)
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		val decision = volumeKeySkipDecision(
			enabled = preferenceManager.volumeKeysSkipTracks,
			key = event.toVolumeKeySkipKey(),
			eventAction = event.toVolumeKeySkipEventAction(),
			repeatCount = event.repeatCount
		)

		when (decision.skipAction) {
			VolumeKeySkipAction.Next -> player.next()
			VolumeKeySkipAction.Previous -> player.previous()
			null -> Unit
		}

		return if (decision.consume) true else super.dispatchKeyEvent(event)
	}

	override fun onUserLeaveHint() {
		LidaClipPictureInPictureCoordinator.onUserLeaveHint(this)
		super.onUserLeaveHint()
	}

	private fun applyReaderDevIntentSeed(intent: Intent?) {
		if (!BuildConfig.NAVIC_READER_DEV || intent == null) {
			return
		}
		var applied = false
		var binderySeeded = false
		val binderyOpdsUrl = intent.stringExtra(
			ReaderDevExtraBinderyOpdsUrl,
			"BINDERY_OPDS_URL",
			"BINDERY_OPDS_BASE_URL"
		)
		val binderyApiKey = intent.stringExtra(ReaderDevExtraBinderyApiKey, "BINDERY_API_KEY")
		val binderyLanguage = intent.stringExtra(ReaderDevExtraBinderyLanguage, "BINDERY_LANGUAGE_FILTER")

		if (!binderyOpdsUrl.isNullOrBlank()) {
			preferenceManager.binderyOpdsBaseUrl = binderyOpdsUrl.trim()
			preferenceManager.binderyEnabled = true
			binderySeeded = true
			applied = true
		}
		if (!binderyApiKey.isNullOrBlank()) {
			preferenceManager.binderyApiKey = binderyApiKey.trim()
			preferenceManager.binderyEnabled = true
			binderySeeded = true
			applied = true
		}
		if (!binderyLanguage.isNullOrBlank()) {
			preferenceManager.binderyLanguageFilter = binderyLanguage.trim()
			applied = true
		}
		if (intent.hasExtra(ReaderDevExtraReaderWebDebugging)) {
			preferenceManager.readerWebContentsDebuggingEnabled =
				intent.getBooleanExtra(ReaderDevExtraReaderWebDebugging, true)
			applied = true
		} else {
			preferenceManager.readerWebContentsDebuggingEnabled = true
		}
		val directReaderScreen = intent.toReaderDevInitialScreen()
		if (directReaderScreen != null) {
			readerDevInitialScreen = directReaderScreen
			applied = true
		} else if (binderySeeded || preferenceManager.binderyEnabled) {
			readerDevInitialScreen = Screen.BinderyBooks
			applied = true
		}
		if (applied) {
			Log.i("MainActivity", "Applied readerDev seed extras")
		}
	}
}

private const val ReaderDevExtraBinderyOpdsUrl = "navic.dev.bindery.opds_url"
private const val ReaderDevExtraBinderyApiKey = "navic.dev.bindery.api_key"
private const val ReaderDevExtraBinderyLanguage = "navic.dev.bindery.language_filter"
private const val ReaderDevExtraReaderWebDebugging = "navic.dev.reader.web_debugging"
private const val ReaderDevExtraPublicationUrl = "navic.dev.reader.publication_url"
private const val ReaderDevExtraBookId = "navic.dev.reader.book_id"
private const val ReaderDevExtraResourceHref = "navic.dev.reader.resource_href"
private const val ReaderDevExtraTitle = "navic.dev.reader.title"
private const val ReaderDevExtraKind = "navic.dev.reader.kind"
private const val ReaderDevExtraFormat = "navic.dev.reader.format"
private const val ReaderDevExtraStartHref = "navic.dev.reader.start_href"
private const val ReaderDevExtraStartCfi = "navic.dev.reader.start_cfi"
private const val ReaderDevExtraStartProgress = "navic.dev.reader.start_progress"

private fun Intent.stringExtra(primaryKey: String, vararg fallbackKeys: String): String? {
	getStringExtra(primaryKey)?.let { return it }
	for (fallbackKey in fallbackKeys) {
		getStringExtra(fallbackKey)?.let { return it }
	}
	return null
}

private fun Intent.toReaderDevInitialScreen(): Screen.Reader? {
	val publicationUrl = stringExtra(ReaderDevExtraPublicationUrl, "NAVIC_READER_DEV_PUBLICATION_URL")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: return null
	val resourceHref = stringExtra(ReaderDevExtraResourceHref, "NAVIC_READER_DEV_RESOURCE_HREF")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: publicationUrl
	val bookId = stringExtra(ReaderDevExtraBookId, "NAVIC_READER_DEV_BOOK_ID")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: "reader-dev"
	val title = stringExtra(ReaderDevExtraTitle, "NAVIC_READER_DEV_TITLE")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: "Reader Dev"
	return Screen.Reader(
		title = title,
		publicationUrl = publicationUrl,
		bookId = bookId,
		resourceHref = resourceHref,
		kind = readerDevPublicationKind(),
		publicationFormat = readerDevPublicationFormat(),
		mediaOverlayEnabled = readerDevPublicationKind() == ReaderPublicationKind.Readaloud,
		startCfi = stringExtra(ReaderDevExtraStartCfi, "NAVIC_READER_DEV_START_CFI")?.trim()?.takeIf { it.isNotEmpty() },
		startHref = stringExtra(ReaderDevExtraStartHref, "NAVIC_READER_DEV_START_HREF")?.trim()?.takeIf { it.isNotEmpty() },
		startProgress = stringExtra(ReaderDevExtraStartProgress, "NAVIC_READER_DEV_START_PROGRESS")
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.toDoubleOrNull()
			?.takeIf(Double::isFinite)
			?.coerceIn(0.0, 1.0)
	)
}

private fun Intent.readerDevPublicationKind(): ReaderPublicationKind =
	when (stringExtra(ReaderDevExtraKind, "NAVIC_READER_DEV_KIND")?.trim()?.lowercase()) {
		"readaloud", "media-overlay", "media_overlay" -> ReaderPublicationKind.Readaloud
		else -> ReaderPublicationKind.Ebook
	}

private fun Intent.readerDevPublicationFormat(): ReaderPublicationFormat =
	when (stringExtra(ReaderDevExtraFormat, "NAVIC_READER_DEV_FORMAT")?.trim()?.lowercase()) {
		"pdf" -> ReaderPublicationFormat.Pdf
		else -> ReaderPublicationFormat.Epub
	}

private fun KeyEvent.toVolumeKeySkipKey(): VolumeKeySkipKey =
	when (keyCode) {
		KeyEvent.KEYCODE_VOLUME_UP -> VolumeKeySkipKey.VolumeUp
		KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKeySkipKey.VolumeDown
		else -> VolumeKeySkipKey.Other
	}

private fun KeyEvent.toVolumeKeySkipEventAction(): VolumeKeySkipEventAction =
	when (action) {
		KeyEvent.ACTION_DOWN -> VolumeKeySkipEventAction.Down
		KeyEvent.ACTION_UP -> VolumeKeySkipEventAction.Up
		else -> VolumeKeySkipEventAction.Other
	}
