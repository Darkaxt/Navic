package android.webkit

/** Test-only subclass in the SDK constructor's package; values stay synthetic and in memory. */
public class ReaderSyntheticWebResourceError(
	private val suppliedCode: Int,
	private val suppliedDescription: CharSequence,
	private val rejectDescriptionRead: Boolean = false,
) : WebResourceError() {
	override fun getErrorCode(): Int = suppliedCode
	override fun getDescription(): CharSequence {
		check(!rejectDescriptionRead) { "RESOURCE_DESCRIPTION_MUST_NOT_BE_READ" }
		return suppliedDescription
	}
}
