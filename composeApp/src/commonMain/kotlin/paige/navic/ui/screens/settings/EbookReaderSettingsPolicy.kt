package paige.navic.ui.screens.settings

data class EbookReaderSettingDescriptor(
	val id: String,
	val title: String,
	val subtitle: String,
	val keywords: List<String>
) {
	fun toSearchEntry(path: String): SettingsSearchEntryText =
		SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		)
}

fun ebookReaderSettingDescriptors(): List<EbookReaderSettingDescriptor> =
	listOf(
		EbookReaderSettingDescriptor(
			id = "ebooks.pdf-fit",
			title = "PDF/Image fit",
			subtitle = "Default scaling for PDF and fixed image pages.",
			keywords = listOf("reader", "PDF", "image", "page fit", "height", "width", "original")
		),
		EbookReaderSettingDescriptor(
			id = "ebooks.pdf-crop-borders",
			title = "Crop PDF/Image borders",
			subtitle = "Slightly enlarge image-backed pages to hide scanned borders.",
			keywords = listOf("reader", "PDF", "image", "crop", "borders", "scan")
		),
		EbookReaderSettingDescriptor(
			id = "ebooks.pdf-page-gap",
			title = "PDF/Image page gap",
			subtitle = "Extra vertical spacing between PDF or image pages.",
			keywords = listOf("reader", "PDF", "image", "gap", "spacing")
		)
	)

fun ebookReaderSettingDescriptor(id: String): EbookReaderSettingDescriptor =
	ebookReaderSettingDescriptors().first { descriptor -> descriptor.id == id }
