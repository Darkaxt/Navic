package paige.navic.shared

/** An automatic pause owns one possible resume, never a later user's pause. */
internal class PlaybackAutoResumeIntent {
	class Token internal constructor(
		internal val session: Any,
		internal val item: Any?,
		internal val index: Int
	)

	private var active: Token? = null

	fun invalidate() {
		active = null
	}

	fun capture(session: Any, item: Any?, index: Int): Token =
		Token(session, item, index).also { active = it }

	fun consume(token: Token?, session: Any, item: Any?, index: Int): Boolean {
		if (token == null || active !== token || token.session !== session ||
			token.item != item || token.index != index) return false
		active = null
		return true
	}
}
