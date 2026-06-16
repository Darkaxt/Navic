package paige.navic.ui.screens.reader

import paige.navic.domain.manager.PreferenceManager
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.clearReaderBookSettings
import paige.navic.reader.normalizedReaderSettings
import paige.navic.reader.readerBookSettings
import paige.navic.reader.readerDefaultSettings
import paige.navic.reader.readerSettingsForBook
import paige.navic.reader.setReaderBookSettings
import paige.navic.reader.setReaderDefaultSettings

internal fun readerHasBookSettings(
	preferenceManager: PreferenceManager,
	bookId: String
): Boolean = preferenceManager.readerBookSettings(bookId) != null

internal fun readerInitialSettingsScope(hasBookSettings: Boolean): ReaderSettingsScope =
	if (hasBookSettings) {
		ReaderSettingsScope.Book
	} else {
		ReaderSettingsScope.Global
	}

internal fun PreferenceManager.readerSettingsForScope(
	bookId: String,
	scope: ReaderSettingsScope
): ReaderSettings =
	when (scope) {
		ReaderSettingsScope.Global -> readerDefaultSettings()
		ReaderSettingsScope.Book -> readerSettingsForBook(bookId)
	}

internal fun PreferenceManager.persistReaderSettingsForScope(
	bookId: String,
	scope: ReaderSettingsScope,
	settings: ReaderSettings
): ReaderSettings {
	val normalized = settings.normalizedReaderSettings()
	when (scope) {
		ReaderSettingsScope.Global -> setReaderDefaultSettings(normalized)
		ReaderSettingsScope.Book -> setReaderBookSettings(bookId, normalized)
	}
	return normalized
}

internal fun PreferenceManager.readerSettingsForSelectedScope(
	bookId: String,
	currentSettings: ReaderSettings,
	scope: ReaderSettingsScope,
	hasBookSettings: Boolean
): ReaderSettings =
	when (scope) {
		ReaderSettingsScope.Global -> readerDefaultSettings()
		ReaderSettingsScope.Book -> {
			if (!hasBookSettings) {
				setReaderBookSettings(bookId, currentSettings)
			}
			readerSettingsForBook(bookId)
		}
	}

internal fun PreferenceManager.resetReaderBookSettingsToGlobal(bookId: String): ReaderSettings {
	clearReaderBookSettings(bookId)
	return readerDefaultSettings()
}
