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
			id = "ebooks.paper-texture",
			title = "Paper texture",
			subtitle = "Show the reader paper grain texture.",
			keywords = listOf("reader", "ebook", "paper", "cover", "texture", "grain", "overlay", "appearance")
		),
		EbookReaderSettingDescriptor(
			id = "ebooks.page-edges",
			title = "Page edges",
			subtitle = "Show worn page-edge shading.",
			keywords = listOf("reader", "ebook", "paper", "cover", "page", "edges", "border", "worn", "overlay", "appearance")
		),
		EbookReaderSettingDescriptor(
			id = "ebooks.paper-stains",
			title = "Paper stains",
			subtitle = "Show subtle stains and use marks.",
			keywords = listOf("reader", "ebook", "paper", "cover", "stains", "marks", "wear", "overlay", "appearance")
		),
		EbookReaderSettingDescriptor(
			id = "ebooks.cover-backdrop",
			title = "Cover backdrop",
			subtitle = "Fill cover-page margins with a blurred copy of the cover.",
			keywords = listOf("reader", "ebook", "cover", "backdrop", "blur", "background", "paper", "appearance")
		),
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
