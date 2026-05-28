package paige.navic.domain.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_clear_downloads
import navic.composeapp.generated.resources.action_clear_image_cache
import navic.composeapp.generated.resources.action_clear_pending_actions
import navic.composeapp.generated.resources.action_rebuild_database
import navic.composeapp.generated.resources.info_clear_downloads_confirmation
import navic.composeapp.generated.resources.info_clear_image_cache_confirmation
import navic.composeapp.generated.resources.info_clear_pending_actions_confirmation
import navic.composeapp.generated.resources.info_rebuild_database_confirmation
import org.jetbrains.compose.resources.StringResource

enum class DangerZoneAction(
	val title: StringResource,
	val confirmationMessage: StringResource,
	val requiresConfirmation: Boolean = true
) {
	ClearImageCache(
		Res.string.action_clear_image_cache,
		Res.string.info_clear_image_cache_confirmation
	),
	ClearPendingSyncActions(
		Res.string.action_clear_pending_actions,
		Res.string.info_clear_pending_actions_confirmation
	),
	ClearDownloads(
		Res.string.action_clear_downloads,
		Res.string.info_clear_downloads_confirmation
	),
	RebuildDatabase(
		Res.string.action_rebuild_database,
		Res.string.info_rebuild_database_confirmation
	)
}

fun dangerZoneActions(): ImmutableList<DangerZoneAction> =
	persistentListOf(
		DangerZoneAction.ClearImageCache,
		DangerZoneAction.ClearPendingSyncActions,
		DangerZoneAction.ClearDownloads,
		DangerZoneAction.RebuildDatabase
	)
