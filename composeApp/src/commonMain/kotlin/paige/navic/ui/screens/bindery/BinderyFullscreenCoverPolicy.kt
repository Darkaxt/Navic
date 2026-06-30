package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPropertyBag
import paige.navic.domain.repositories.BinderyPropertyValue
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.ui.screens.bindery.versionpolicy.firstNonBlankValue
import kotlin.math.abs

private val BinderyFullscreenCoverPropertyKeys = arrayOf(
	"fullscreenCoverUrl",
	"fullscreenCoverHref",
	"fullscreenCover",
	"extendedCoverUrl",
	"extendedCoverHref",
	"extendedCover",
	"expandedCoverUrl",
	"expandedCoverHref",
	"expandedCover",
	"shellCoverUrl",
	"shellCoverHref",
	"shellCover"
)

private val BinderyFullscreenCoverVariantPropertyKeys = arrayOf(
	"fullscreenCoverVariants",
	"extendedCoverVariants",
	"expandedCoverVariants",
	"shellCoverVariants"
)

private val BinderyFullscreenCoverVariantHrefKeys = arrayOf(
	"href",
	"url",
	"fullscreenCoverUrl",
	"fullscreenCoverHref",
	"extendedCoverUrl",
	"extendedCoverHref",
	"expandedCoverUrl",
	"expandedCoverHref",
	"shellCoverUrl",
	"shellCoverHref"
)

private val BinderyFullscreenCoverVariantWidthKeys = arrayOf(
	"width",
	"widthPx",
	"pixelWidth",
	"w"
)

private val BinderyFullscreenCoverVariantHeightKeys = arrayOf(
	"height",
	"heightPx",
	"pixelHeight",
	"h"
)

private val BinderyFullscreenCoverVariantAspectKeys = arrayOf(
	"aspectRatio",
	"aspect",
	"ratio"
)

private val BinderyFullscreenCoverRelTokens = setOf(
	"fullscreen-cover",
	"cover-fullscreen",
	"extended-cover",
	"cover-extended",
	"expanded-cover",
	"cover-expanded",
	"shell-cover",
	"cover-shell"
)

internal fun binderyFullscreenCoverTargetAspectRatio(
	widthDp: Float,
	heightDp: Float
): Double? {
	if (widthDp <= 0f || heightDp <= 0f) return null
	return widthDp.toDouble() / heightDp.toDouble()
}

internal fun List<BinderyBookVersionRow>.withFullscreenCoverRenditions(
	fullscreenCoverHref: String?,
	fullscreenCoverVariants: List<BinderyFullscreenCoverVariant>
): List<BinderyBookVersionRow> {
	val href = fullscreenCoverHref?.trim()?.takeIf { it.isNotEmpty() }
	val variants = fullscreenCoverVariants
		.mapNotNull { variant ->
			val variantHref = variant.href.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			variant.copy(href = variantHref)
		}
		.distinctBy { variant -> variant.href }
	val fallbackHref = href ?: variants.firstOrNull()?.href ?: return this
	return map { row ->
		row.copy(
			fullscreenCoverHref = row.fullscreenCoverHref?.takeIf { it.isNotBlank() } ?: fallbackHref,
			fullscreenCoverVariants = row.fullscreenCoverVariants.ifEmpty { variants }
		)
	}
}

internal fun BinderyBookVersionRow.fullscreenCoverUrl(
	opdsBaseUrl: String,
	fullscreenCoverTargetAspectRatio: Double? = null
): String? {
	val targetAspectRatio = fullscreenCoverTargetAspectRatio?.takeIf { it > 0.0 }
	val variantHref = fullscreenCoverVariants
		.selectFullscreenCoverVariant(targetAspectRatio)
		?.href
	val href = if (targetAspectRatio != null) {
		variantHref ?: fullscreenCoverHref
	} else {
		fullscreenCoverHref ?: variantHref
	}
	return href
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.let { href -> binderyEndpoint(opdsBaseUrl, href) }
}

internal fun BinderyManifest.fullscreenCoverHref(): String? =
	properties.firstNonBlankValue(*BinderyFullscreenCoverPropertyKeys) ?:
		propertyValues.firstNonBlankString(*BinderyFullscreenCoverPropertyKeys) ?:
		(images + links).firstNotNullOfOrNull(BinderyLink::fullscreenCoverHref)

internal fun BinderyManifest.fullscreenCoverVariants(): List<BinderyFullscreenCoverVariant> =
	buildList {
		addAll(propertyValues.fullscreenCoverVariants())
		addAll((images + links).mapNotNull(BinderyLink::fullscreenCoverVariant))
	}.distinctBy { variant -> variant.href }

private fun BinderyLink.fullscreenCoverHref(): String? =
	if (rel.any(String::isBinderyFullscreenCoverRel)) {
		href.trim().takeIf { it.isNotEmpty() }
	} else {
		properties.firstNonBlankValue(*BinderyFullscreenCoverPropertyKeys)
			?: propertyValues.firstNonBlankString(*BinderyFullscreenCoverPropertyKeys)
	}

private fun BinderyLink.fullscreenCoverVariant(): BinderyFullscreenCoverVariant? {
	val variantHref = fullscreenCoverHref()?.takeIf { it.isNotBlank() } ?: return null
	return BinderyFullscreenCoverVariant(
		href = variantHref,
		widthPx = properties.firstDouble(*BinderyFullscreenCoverVariantWidthKeys)
			?: propertyValues.firstNumber(*BinderyFullscreenCoverVariantWidthKeys),
		heightPx = properties.firstDouble(*BinderyFullscreenCoverVariantHeightKeys)
			?: propertyValues.firstNumber(*BinderyFullscreenCoverVariantHeightKeys),
		aspectRatio = properties.firstDouble(*BinderyFullscreenCoverVariantAspectKeys)
			?: propertyValues.firstNumber(*BinderyFullscreenCoverVariantAspectKeys)
	)
}

private fun String.isBinderyFullscreenCoverRel(): Boolean {
	val normalized = trim().lowercase().substringAfterLast('/')
	return normalized in BinderyFullscreenCoverRelTokens
}

private fun BinderyPropertyBag.fullscreenCoverVariants(): List<BinderyFullscreenCoverVariant> =
	BinderyFullscreenCoverVariantPropertyKeys.flatMap { key ->
		array(key).mapNotNull { value -> value.toFullscreenCoverVariant() }
	}

private fun BinderyPropertyValue.toFullscreenCoverVariant(): BinderyFullscreenCoverVariant? =
	when (this) {
		is BinderyPropertyValue.ObjectValue -> BinderyPropertyBag(values).toFullscreenCoverVariant()
		is BinderyPropertyValue.StringValue -> value
			.trim()
			.takeIf { it.isNotEmpty() }
			?.let(::BinderyFullscreenCoverVariant)
		else -> null
	}

private fun BinderyPropertyBag.toFullscreenCoverVariant(): BinderyFullscreenCoverVariant? {
	val href = firstNonBlankString(*BinderyFullscreenCoverVariantHrefKeys) ?: return null
	return BinderyFullscreenCoverVariant(
		href = href,
		widthPx = firstNumber(*BinderyFullscreenCoverVariantWidthKeys),
		heightPx = firstNumber(*BinderyFullscreenCoverVariantHeightKeys),
		aspectRatio = firstNumber(*BinderyFullscreenCoverVariantAspectKeys)
	)
}

private fun List<BinderyFullscreenCoverVariant>.selectFullscreenCoverVariant(
	targetAspectRatio: Double?
): BinderyFullscreenCoverVariant? {
	val candidates = filter { variant -> variant.href.isNotBlank() }
	if (candidates.isEmpty()) return null
	val target = targetAspectRatio?.takeIf { it > 0.0 } ?: return candidates.first()
	return candidates.minWithOrNull(
		compareBy<BinderyFullscreenCoverVariant> { variant ->
			variant.effectiveAspectRatio?.let { ratio -> abs(ratio - target) } ?: Double.MAX_VALUE
		}.thenByDescending { variant -> variant.widthPx ?: 0.0 }
			.thenByDescending { variant -> variant.heightPx ?: 0.0 }
			.thenBy { variant -> variant.href }
	)
}

private fun BinderyPropertyBag.firstNonBlankString(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { key -> string(key)?.trim()?.takeIf { it.isNotEmpty() } }

private fun BinderyPropertyBag.firstNumber(vararg keys: String): Double? =
	keys.firstNotNullOfOrNull { key -> number(key)?.takeIf { it > 0.0 } }

private fun Map<String, String>.firstDouble(vararg keys: String): Double? =
	keys.firstNotNullOfOrNull { key -> firstNonBlankValue(key)?.toDoubleOrNull()?.takeIf { it > 0.0 } }
