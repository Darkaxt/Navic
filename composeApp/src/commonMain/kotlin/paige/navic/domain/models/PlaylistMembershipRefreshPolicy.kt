package paige.navic.domain.models

fun playlistIdsToRefreshAfterMembershipUpdate(playlistIds: Iterable<String>): List<String> =
	playlistIds
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.distinct()
