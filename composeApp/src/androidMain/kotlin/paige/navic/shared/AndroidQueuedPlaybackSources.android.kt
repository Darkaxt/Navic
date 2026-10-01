package paige.navic.shared

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player

internal data class QueuedPlaybackSourceUpdate(
	val index: Int,
	val mediaId: String,
	val previousUri: Uri?,
	val uri: Uri
)

internal fun queuedPlaybackSourceArgs(
	ownerId: String,
	ownerRevision: Long,
	updates: List<QueuedPlaybackSourceUpdate>
): Bundle = Bundle().apply {
	putString("ownerId", ownerId)
	putLong("ownerRevision", ownerRevision)
	putIntArray("indexes", updates.map { it.index }.toIntArray())
	putStringArrayList("mediaIds", ArrayList(updates.map { it.mediaId }))
	putStringArrayList("previousUris", ArrayList(updates.map { it.previousUri?.toString().orEmpty() }))
	putStringArrayList("uris", ArrayList(updates.map { it.uri.toString() }))
}

internal fun applyQueuedPlaybackSources(
	player: Player,
	args: Bundle,
	ownerId: String?,
	ownerRevision: Long
): Boolean {
	if (ownerId == null || args.getString("ownerId") != ownerId ||
		args.getLong("ownerRevision", -1) != ownerRevision) return false
	val indexes = args.getIntArray("indexes") ?: return false
	val ids = args.getStringArrayList("mediaIds") ?: return false
	val previousUris = args.getStringArrayList("previousUris") ?: return false
	val uris = args.getStringArrayList("uris") ?: return false
	if (indexes.size != ids.size || indexes.size != previousUris.size || indexes.size != uris.size) return false
	val currentIndex = player.currentMediaItemIndex
	val replacements = indexes.indices.mapNotNull { n ->
		val index = indexes[n]
		if (index !in 0 until player.mediaItemCount || index == currentIndex) return@mapNotNull null
		val item = player.getMediaItemAt(index)
		if (item.mediaId != ids[n] || item.localConfiguration?.uri?.toString().orEmpty() != previousUris[n] ||
			uris[n].isEmpty() || uris[n] == previousUris[n]) return@mapNotNull null
		index to item.buildUpon().setUri(Uri.parse(uris[n])).build()
	}.toMap()
	// Never include the active source in a replacement range, even if the controller snapshot is stale.
	for (range in listOf(0 until currentIndex, (currentIndex + 1) until player.mediaItemCount)) {
		val changed = replacements.keys.filter { it in range }
		if (changed.isEmpty()) continue
		val from = changed.min()
		val to = changed.max() + 1
		player.replaceMediaItems(from, to, (from until to).map { replacements[it] ?: player.getMediaItemAt(it) })
	}
	return true
}
